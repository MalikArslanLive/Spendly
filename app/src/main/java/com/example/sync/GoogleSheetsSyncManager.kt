package com.example.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.data.Expense
import com.example.data.ExpenseRepository
import com.example.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

enum class SyncState {
    NOT_CONNECTED,
    SYNCED,
    PENDING,
    SYNCING,
    FAILED
}

data class SyncStatus(
    val state: SyncState = SyncState.NOT_CONNECTED,
    val accountEmail: String? = null,
    val spreadsheetId: String? = null,
    val spreadsheetTitle: String? = null,
    val pendingCount: Int = 0,
    val lastSyncTime: String? = null,
    val errorMessage: String? = null
)

class GoogleSheetsSyncManager(
    private val context: Context,
    private val repository: ExpenseRepository,
    private val scope: CoroutineScope
) {
    companion object {
        private const val PREFS_NAME = "google_sheets_multiuser_prefs"
        private const val KEY_ACTIVE_USER = "active_user_email"

        // Default title for auto-created user spreadsheet
        const val DEFAULT_SPREADSHEET_TITLE = "My Expenses"

        // Google Apps Script template provided as an alternative lightweight deployment
        fun getAppsScriptTemplate(spreadsheetId: String): String = """
// Google Apps Script for user's spreadsheet ($spreadsheetId)
function doPost(e) {
  try {
    var data = JSON.parse(e.postData.contents);
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var expenses = data.expenses || [];
    
    var monthlyGroups = {};
    for (var i = 0; i < expenses.length; i++) {
      var exp = expenses[i];
      var tab = exp.monthTab || "Expenses";
      if (!monthlyGroups[tab]) monthlyGroups[tab] = [];
      monthlyGroups[tab].push(exp);
    }
    
    for (var monthTab in monthlyGroups) {
      var sheet = ss.getSheetByName(monthTab);
      if (!sheet) {
        sheet = ss.insertSheet(monthTab);
        sheet.appendRow(["ID", "Date", "Time", "Amount", "Category", "Note"]);
        sheet.getRange("A1:F1").setFontWeight("bold").setBackground("#D1FAE5");
        sheet.setFrozenRows(1);
      }
      
      var items = monthlyGroups[monthTab];
      for (var j = 0; j < items.length; j++) {
        var item = items[j];
        var idStr = String(item.id);
        var dataRange = sheet.getDataRange();
        var values = dataRange.getValues();
        var foundRow = -1;
        for (var r = 1; r < values.length; r++) {
          if (String(values[r][0]) === idStr) {
            foundRow = r + 1;
            break;
          }
        }
        
        if (item.isDeleted) {
          if (foundRow > 1) {
            sheet.deleteRow(foundRow);
          }
        } else {
          var rowData = [item.id, item.date, item.time, item.amount, item.category, item.note];
          if (foundRow > 1) {
            sheet.getRange(foundRow, 1, 1, 6).setValues([rowData]);
          } else {
            sheet.appendRow(rowData);
          }
        }
      }
      updateMonthlyTotal(sheet);
    }
    return ContentService.createTextOutput(JSON.stringify({status: "success"})).setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({status: "error", message: err.toString()})).setMimeType(ContentService.MimeType.JSON);
  }
}

function updateMonthlyTotal(sheet) {
  var lastRow = sheet.getLastRow();
  var sum = 0;
  if (lastRow > 1) {
    var amounts = sheet.getRange(2, 4, lastRow - 1, 1).getValues();
    for (var i = 0; i < amounts.length; i++) {
      var val = Number(amounts[i][0]);
      if (!isNaN(val)) sum += val;
    }
  }
  sheet.getRange("H1").setValue("Monthly Total");
  sheet.getRange("H2").setValue("Rs. " + sum);
  sheet.getRange("H1:H2").setFontWeight("bold").setBackground("#FEF3C7");
}
        """.trimIndent()
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _syncStatus = MutableStateFlow(loadInitialStatus())
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private var syncJob: Job? = null

    init {
        registerNetworkCallback()
        checkPendingCount()
    }

    private fun loadInitialStatus(): SyncStatus {
        val activeEmail = prefs.getString(KEY_ACTIVE_USER, null)
        return if (activeEmail.isNullOrBlank()) {
            SyncStatus(state = SyncState.NOT_CONNECTED)
        } else {
            val sheetId = prefs.getString("sheet_id_$activeEmail", null)
            val sheetTitle = prefs.getString("sheet_title_$activeEmail", DEFAULT_SPREADSHEET_TITLE)
            val lastSync = prefs.getString("last_sync_$activeEmail", null)
            SyncStatus(
                state = SyncState.SYNCED,
                accountEmail = activeEmail,
                spreadsheetId = sheetId,
                spreadsheetTitle = sheetTitle,
                lastSyncTime = lastSync
            )
        }
    }

    // Connect user's own Google account
    fun connectAccount(
        email: String,
        accessToken: String? = null,
        spreadsheetId: String? = null,
        spreadsheetTitle: String? = null
    ) {
        val cleanEmail = email.trim().lowercase()
        prefs.edit().putString(KEY_ACTIVE_USER, cleanEmail).apply()

        if (!accessToken.isNullOrBlank()) {
            prefs.edit().putString("auth_token_$cleanEmail", accessToken.trim()).apply()
        }

        val existingSheetId = spreadsheetId ?: prefs.getString("sheet_id_$cleanEmail", null)
        val finalSheetId = if (!existingSheetId.isNullOrBlank()) {
            existingSheetId
        } else {
            // Generate or default user's spreadsheet ID placeholder if not provided yet
            "user_sheet_${cleanEmail.replace("@", "_").replace(".", "_")}"
        }
        val finalTitle = spreadsheetTitle ?: prefs.getString("sheet_title_$cleanEmail", DEFAULT_SPREADSHEET_TITLE) ?: DEFAULT_SPREADSHEET_TITLE

        prefs.edit()
            .putString("sheet_id_$cleanEmail", finalSheetId)
            .putString("sheet_title_$cleanEmail", finalTitle)
            .apply()

        _syncStatus.value = _syncStatus.value.copy(
            state = SyncState.PENDING,
            accountEmail = cleanEmail,
            spreadsheetId = finalSheetId,
            spreadsheetTitle = finalTitle,
            errorMessage = null
        )

        checkPendingCount()
        syncNow()
    }

    // Disconnect user's Google account
    fun disconnectAccount() {
        val activeEmail = prefs.getString(KEY_ACTIVE_USER, null)
        if (!activeEmail.isNullOrBlank()) {
            // Clear current active session user
            prefs.edit().remove(KEY_ACTIVE_USER).apply()
        }

        _syncStatus.value = SyncStatus(
            state = SyncState.NOT_CONNECTED,
            accountEmail = null,
            spreadsheetId = null,
            spreadsheetTitle = null,
            pendingCount = 0,
            lastSyncTime = null,
            errorMessage = null
        )
    }

    // Allow user to change or create their own spreadsheet
    fun setSpreadsheet(spreadsheetId: String, title: String = DEFAULT_SPREADSHEET_TITLE) {
        val activeEmail = _syncStatus.value.accountEmail ?: return
        val cleanId = spreadsheetId.trim()
        val cleanTitle = title.trim().ifBlank { DEFAULT_SPREADSHEET_TITLE }

        prefs.edit()
            .putString("sheet_id_$activeEmail", cleanId)
            .putString("sheet_title_$activeEmail", cleanTitle)
            .apply()

        _syncStatus.value = _syncStatus.value.copy(
            spreadsheetId = cleanId,
            spreadsheetTitle = cleanTitle,
            state = SyncState.PENDING
        )

        syncNow()
    }

    fun getWebhookUrl(): String {
        val activeEmail = _syncStatus.value.accountEmail ?: return ""
        return prefs.getString("webhook_url_$activeEmail", "") ?: ""
    }

    fun setWebhookUrl(url: String) {
        val activeEmail = _syncStatus.value.accountEmail ?: return
        prefs.edit().putString("webhook_url_$activeEmail", url.trim()).apply()
        checkPendingCount()
    }

    fun getAuthToken(): String? {
        val activeEmail = _syncStatus.value.accountEmail ?: return null
        return prefs.getString("auth_token_$activeEmail", null)
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun registerNetworkCallback() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // When online, auto-sync pending changes if account is connected
                scope.launch {
                    if (_syncStatus.value.state != SyncState.NOT_CONNECTED) {
                        val pending = repository.getPendingSyncExpenses()
                        if (pending.isNotEmpty()) {
                            syncNow()
                        }
                    }
                }
            }
        })
    }

    fun checkPendingCount() {
        scope.launch {
            if (_syncStatus.value.state == SyncState.NOT_CONNECTED) {
                return@launch
            }
            val pending = repository.getPendingSyncExpenses()
            val state = if (pending.isEmpty()) SyncState.SYNCED else SyncState.PENDING
            _syncStatus.value = _syncStatus.value.copy(
                state = state,
                pendingCount = pending.size
            )
        }
    }

    fun syncNow() {
        if (_syncStatus.value.state == SyncState.NOT_CONNECTED) {
            return
        }

        if (syncJob?.isActive == true) return

        syncJob = scope.launch {
            _syncStatus.value = _syncStatus.value.copy(state = SyncState.SYNCING)

            val pendingExpenses = repository.getPendingSyncExpenses()
            val activeEmail = _syncStatus.value.accountEmail
            val sheetId = _syncStatus.value.spreadsheetId

            if (pendingExpenses.isEmpty()) {
                val nowStr = Formatters.currentTimeString()
                saveLastSyncTime(nowStr)
                _syncStatus.value = _syncStatus.value.copy(
                    state = SyncState.SYNCED,
                    pendingCount = 0,
                    lastSyncTime = nowStr
                )
                return@launch
            }

            if (!isOnline()) {
                _syncStatus.value = _syncStatus.value.copy(
                    state = SyncState.PENDING,
                    pendingCount = pendingExpenses.size,
                    errorMessage = "Offline. Changes saved locally. Will sync when online."
                )
                return@launch
            }

            val authToken = getAuthToken()
            val webhookUrl = getWebhookUrl()

            val success = withContext(Dispatchers.IO) {
                try {
                    if (!authToken.isNullOrBlank() && !sheetId.isNullOrBlank()) {
                        // Method 1: Direct Google Sheets API with user's OAuth access token
                        syncViaGoogleSheetsApi(sheetId, authToken, pendingExpenses)
                    } else if (webhookUrl.isNotBlank() && !sheetId.isNullOrBlank()) {
                        // Method 2: Google Apps Script Web App endpoint deployed on user's own spreadsheet
                        syncViaWebhookEndpoint(webhookUrl, sheetId, pendingExpenses)
                    } else {
                        // Neither Webhook nor OAuth Token is configured yet for network transmission
                        // Mark as pending with clear user guidance
                        false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }

            if (success) {
                val ids = pendingExpenses.map { it.id }
                repository.markExpensesSynced(ids)
                val nowStr = Formatters.currentTimeString()
                saveLastSyncTime(nowStr)
                _syncStatus.value = _syncStatus.value.copy(
                    state = SyncState.SYNCED,
                    pendingCount = 0,
                    lastSyncTime = nowStr,
                    errorMessage = null
                )
            } else {
                val errorMsg = if (authToken.isNullOrBlank() && webhookUrl.isBlank()) {
                    "Google Sheets API or Webhook URL not configured. Tap Settings to complete setup."
                } else {
                    "Sync failed. Will retry automatically."
                }
                _syncStatus.value = _syncStatus.value.copy(
                    state = SyncState.FAILED,
                    pendingCount = pendingExpenses.size,
                    errorMessage = errorMsg
                )
            }
        }
    }

    private fun syncViaWebhookEndpoint(
        webhookUrl: String,
        spreadsheetId: String,
        expenses: List<Expense>
    ): Boolean {
        return try {
            val payload = JSONObject()
            payload.put("spreadsheetId", spreadsheetId)

            val jsonArray = JSONArray()
            for (exp in expenses) {
                val item = JSONObject()
                item.put("id", exp.id)
                item.put("date", exp.date)
                item.put("time", exp.time)
                item.put("amount", exp.amount)
                item.put("category", exp.category)
                item.put("note", exp.note)
                item.put("isDeleted", exp.isDeleted)

                val monthPrefix = if (exp.date.length >= 7) exp.date.substring(0, 7) else "2026-10"
                item.put("monthTab", Formatters.formatMonthYear(monthPrefix))

                jsonArray.put(item)
            }
            payload.put("expenses", jsonArray)

            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(webhookUrl)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun syncViaGoogleSheetsApi(
        spreadsheetId: String,
        accessToken: String,
        expenses: List<Expense>
    ): Boolean {
        return try {
            // Group expenses by monthly tab
            val monthlyGroups = expenses.groupBy { exp ->
                val monthPrefix = if (exp.date.length >= 7) exp.date.substring(0, 7) else "2026-10"
                Formatters.formatMonthYear(monthPrefix)
            }

            for ((monthTab, items) in monthlyGroups) {
                ensureMonthlyTabExists(spreadsheetId, accessToken, monthTab)

                // Append or update rows for this tab
                val encodedTab = URLEncoder.encode(monthTab, "UTF-8")
                val rows = JSONArray()
                for (item in items) {
                    if (!item.isDeleted) {
                        val row = JSONArray().apply {
                            put(item.id)
                            put(item.date)
                            put(item.time)
                            put(item.amount)
                            put(item.category)
                            put(item.note)
                        }
                        rows.put(row)
                    }
                }

                if (rows.length() > 0) {
                    val appendBody = JSONObject().apply {
                        put("values", rows)
                    }.toString().toRequestBody("application/json".toMediaType())

                    val appendReq = Request.Builder()
                        .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$encodedTab!A:F:append?valueInputOption=USER_ENTERED")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .post(appendBody)
                        .build()

                    val appendRes = client.newCall(appendReq).execute()
                    if (!appendRes.isSuccessful) return false
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun ensureMonthlyTabExists(
        spreadsheetId: String,
        accessToken: String,
        monthTab: String
    ) {
        try {
            // Check existing sheets
            val getReq = Request.Builder()
                .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId?fields=sheets.properties.title")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val getRes = client.newCall(getReq).execute()
            if (getRes.isSuccessful) {
                val json = JSONObject(getRes.body?.string() ?: "{}")
                val sheets = json.optJSONArray("sheets")
                var exists = false
                if (sheets != null) {
                    for (i in 0 until sheets.length()) {
                        val title = sheets.getJSONObject(i).getJSONObject("properties").getString("title")
                        if (title.equals(monthTab, ignoreCase = true)) {
                            exists = true
                            break
                        }
                    }
                }

                if (!exists) {
                    // Create monthly tab
                    val batchBody = JSONObject().apply {
                        val requests = JSONArray().apply {
                            put(JSONObject().apply {
                                put("addSheet", JSONObject().apply {
                                    put("properties", JSONObject().apply {
                                        put("title", monthTab)
                                    })
                                })
                            })
                        }
                        put("requests", requests)
                    }.toString().toRequestBody("application/json".toMediaType())

                    val addReq = Request.Builder()
                        .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId:batchUpdate")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .post(batchBody)
                        .build()

                    client.newCall(addReq).execute()

                    // Add Headers to new tab
                    val encodedTab = URLEncoder.encode(monthTab, "UTF-8")
                    val headerBody = JSONObject().apply {
                        put("values", JSONArray().apply {
                            put(JSONArray().apply {
                                put("ID")
                                put("Date")
                                put("Time")
                                put("Amount")
                                put("Category")
                                put("Note")
                            })
                        })
                    }.toString().toRequestBody("application/json".toMediaType())

                    val headerReq = Request.Builder()
                        .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$encodedTab!A1:F1:append?valueInputOption=USER_ENTERED")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .post(headerBody)
                        .build()

                    client.newCall(headerReq).execute()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveLastSyncTime(time: String) {
        val activeEmail = _syncStatus.value.accountEmail ?: return
        prefs.edit().putString("last_sync_$activeEmail", time).apply()
    }
}
