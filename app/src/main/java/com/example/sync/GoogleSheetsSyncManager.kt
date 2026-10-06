package com.example.sync

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.data.Expense
import com.example.data.ExpenseRepository
import com.example.util.Formatters
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
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
    val spreadsheetUrl: String? = null,
    val spreadsheetType: String? = null, // "created" or "existing"
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

        const val DEFAULT_SPREADSHEET_TITLE = "Spendly"

        const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
        const val SCOPE_SPREADSHEETS = "https://www.googleapis.com/auth/spreadsheets"

        val STANDARD_HEADERS = listOf(
            "Transaction ID",
            "Date",
            "Time",
            "Type",
            "Category",
            "Amount",
            "Note"
        )
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
        if (!activeEmail.isNullOrBlank()) {
            val sheetId = prefs.getString("sheet_id_$activeEmail", null)
            val sheetTitle = prefs.getString("sheet_title_$activeEmail", DEFAULT_SPREADSHEET_TITLE)
            val sheetUrl = prefs.getString("sheet_url_$activeEmail", null)
                ?: if (!sheetId.isNullOrBlank()) "https://docs.google.com/spreadsheets/d/$sheetId/edit" else null
            val sheetType = prefs.getString("sheet_type_$activeEmail", "existing")
            val lastSync = prefs.getString("last_sync_$activeEmail", null)

            return SyncStatus(
                state = if (!sheetId.isNullOrBlank()) SyncState.SYNCED else SyncState.PENDING,
                accountEmail = activeEmail,
                spreadsheetId = sheetId,
                spreadsheetTitle = sheetTitle,
                spreadsheetUrl = sheetUrl,
                spreadsheetType = sheetType,
                lastSyncTime = lastSync
            )
        }
        return SyncStatus(state = SyncState.NOT_CONNECTED)
    }

    /**
     * Builds the official Google Sign-In intent requesting Drive & Sheets scopes.
     */
    fun getGoogleSignInIntent(): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(SCOPE_DRIVE_FILE),
                Scope(SCOPE_SPREADSHEETS)
            )
            .build()
        return GoogleSignIn.getClient(context, gso).signInIntent
    }

    /**
     * Handles the result returned by Google Sign-In activity result launcher.
     */
    fun handleGoogleSignInResult(
        intent: Intent?,
        onSuccess: (GoogleSignInAccount) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
            val account = task.getResult(ApiException::class.java)
            val email = account.email

            if (email.isNullOrBlank()) {
                onError("Failed to obtain account email from Google account.")
                return
            }

            val cleanEmail = email.trim().lowercase()
            prefs.edit().putString(KEY_ACTIVE_USER, cleanEmail).apply()

            val existingSheetId = prefs.getString("sheet_id_$cleanEmail", null)
            val existingTitle = prefs.getString("sheet_title_$cleanEmail", null)
            val existingUrl = prefs.getString("sheet_url_$cleanEmail", null)
                ?: if (!existingSheetId.isNullOrBlank()) "https://docs.google.com/spreadsheets/d/$existingSheetId/edit" else null
            val existingType = prefs.getString("sheet_type_$cleanEmail", "existing")

            _syncStatus.value = SyncStatus(
                state = if (existingSheetId != null) SyncState.SYNCED else SyncState.PENDING,
                accountEmail = cleanEmail,
                spreadsheetId = existingSheetId,
                spreadsheetTitle = existingTitle,
                spreadsheetUrl = existingUrl,
                spreadsheetType = existingType,
                errorMessage = null
            )

            checkPendingCount()
            onSuccess(account)
        } catch (e: ApiException) {
            val detailedMsg = when (e.statusCode) {
                10 -> "Configuration error (Code 10): Ensure SHA-1 is configured in Google Cloud Console for package 'com.aistudio.myexpenses.kfqrz'."
                7 -> "Network error (Code 7): Please check your internet connection."
                12501 -> "Google Sign-In was cancelled."
                12500 -> "Google Sign-In failed (Code 12500): Please check Google Play Services."
                else -> "Google Sign-In failed (Code ${e.statusCode}): ${e.message}"
            }
            _syncStatus.value = SyncStatus(
                state = SyncState.NOT_CONNECTED,
                errorMessage = detailedMsg
            )
            onError(detailedMsg)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Unknown authentication error"
            _syncStatus.value = SyncStatus(
                state = SyncState.NOT_CONNECTED,
                errorMessage = msg
            )
            onError(msg)
        }
    }

    /**
     * Acquires an OAuth 2.0 access token for the authenticated user via GoogleAuthUtil.
     */
    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        val activeEmail = _syncStatus.value.accountEmail ?: return@withContext null
        try {
            val account = Account(activeEmail, "com.google")
            val scopeString = "oauth2:$SCOPE_DRIVE_FILE $SCOPE_SPREADSHEETS"
            GoogleAuthUtil.getToken(context, account, scopeString)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Creates a new dedicated spreadsheet in the authenticated user's Google Drive.
     */
    suspend fun createSpreadsheetInUserDrive(title: String): Result<String> = withContext(Dispatchers.IO) {
        val activeEmail = _syncStatus.value.accountEmail
            ?: return@withContext Result.failure(Exception("No Google account connected. Please authenticate first."))

        val token = getAccessToken()
            ?: return@withContext Result.failure(Exception("Unable to acquire Google OAuth token."))

        try {
            val finalTitle = title.trim().ifBlank { DEFAULT_SPREADSHEET_TITLE }
            val payload = JSONObject().apply {
                put("properties", JSONObject().apply {
                    put("title", finalTitle)
                })
            }
            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("https://sheets.googleapis.com/v4/spreadsheets")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val resJson = JSONObject(response.body?.string() ?: "{}")
                val newSheetId = resJson.getString("spreadsheetId")
                val sheetUrl = resJson.optString("spreadsheetUrl", "https://docs.google.com/spreadsheets/d/$newSheetId/edit")
                setSpreadsheet(newSheetId, finalTitle, sheetUrl, "created")
                Result.success(newSheetId)
            } else {
                val errBody = response.body?.string() ?: ""
                Result.failure(Exception("Google Sheets API error (${response.code}): $errBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verifies that the authenticated user has access to an existing spreadsheet, extracts its metadata, and connects it.
     */
    suspend fun verifyAndConnectExistingSpreadsheet(spreadsheetId: String): Result<String> = withContext(Dispatchers.IO) {
        val activeEmail = _syncStatus.value.accountEmail
            ?: return@withContext Result.failure(Exception("No Google account connected. Please authenticate first."))

        val cleanId = spreadsheetId.trim()
        if (cleanId.isBlank()) {
            return@withContext Result.failure(Exception("Spreadsheet ID cannot be empty."))
        }

        val token = getAccessToken()
            ?: return@withContext Result.failure(Exception("Unable to acquire Google OAuth token."))

        try {
            val request = Request.Builder()
                .url("https://sheets.googleapis.com/v4/spreadsheets/$cleanId?fields=properties.title,spreadsheetUrl")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val resJson = JSONObject(response.body?.string() ?: "{}")
                val title = resJson.optJSONObject("properties")?.optString("title") ?: "Existing Spreadsheet"
                val sheetUrl = resJson.optString("spreadsheetUrl", "https://docs.google.com/spreadsheets/d/$cleanId/edit")
                setSpreadsheet(cleanId, title, sheetUrl, "existing")
                Result.success(title)
            } else {
                Result.failure(Exception("Cannot access spreadsheet (HTTP ${response.code}). Make sure $activeEmail has permission."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Disconnects the user's Google account and clears local session.
     */
    fun disconnectAccount() {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
            GoogleSignIn.getClient(context, gso).signOut()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        prefs.edit().remove(KEY_ACTIVE_USER).apply()

        _syncStatus.value = SyncStatus(
            state = SyncState.NOT_CONNECTED,
            accountEmail = null,
            spreadsheetId = null,
            spreadsheetTitle = null,
            spreadsheetUrl = null,
            spreadsheetType = null,
            pendingCount = 0,
            lastSyncTime = null,
            errorMessage = null
        )
    }

    /**
     * Stores the selected spreadsheet for the active user.
     */
    fun setSpreadsheet(
        spreadsheetId: String,
        title: String = DEFAULT_SPREADSHEET_TITLE,
        url: String? = null,
        type: String = "existing"
    ) {
        val activeEmail = _syncStatus.value.accountEmail ?: return
        val cleanId = spreadsheetId.trim()
        val cleanTitle = title.trim().ifBlank { DEFAULT_SPREADSHEET_TITLE }
        val finalUrl = url ?: "https://docs.google.com/spreadsheets/d/$cleanId/edit"

        prefs.edit()
            .putString("sheet_id_$activeEmail", cleanId)
            .putString("sheet_title_$activeEmail", cleanTitle)
            .putString("sheet_url_$activeEmail", finalUrl)
            .putString("sheet_type_$activeEmail", type)
            .apply()

        _syncStatus.value = _syncStatus.value.copy(
            spreadsheetId = cleanId,
            spreadsheetTitle = cleanTitle,
            spreadsheetUrl = finalUrl,
            spreadsheetType = type,
            state = SyncState.PENDING,
            errorMessage = null
        )

        syncNow()
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isSpreadsheetConnected(): Boolean {
        val status = _syncStatus.value
        return status.state != SyncState.NOT_CONNECTED &&
                !status.spreadsheetId.isNullOrBlank() &&
                !status.accountEmail.isNullOrBlank()
    }

    private fun registerNetworkCallback() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch {
                    if (_syncStatus.value.state != SyncState.NOT_CONNECTED && !_syncStatus.value.spreadsheetId.isNullOrBlank()) {
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
            if (_syncStatus.value.state == SyncState.NOT_CONNECTED) return@launch
            val pending = repository.getPendingSyncExpenses()
            val state = if (pending.isEmpty()) SyncState.SYNCED else SyncState.PENDING
            _syncStatus.value = _syncStatus.value.copy(
                state = state,
                pendingCount = pending.size
            )
        }
    }

    fun syncNow() {
        if (_syncStatus.value.state == SyncState.NOT_CONNECTED) return
        val sheetId = _syncStatus.value.spreadsheetId
        if (sheetId.isNullOrBlank()) {
            _syncStatus.value = _syncStatus.value.copy(
                state = SyncState.PENDING,
                errorMessage = "No spreadsheet connected yet. Please select or create a spreadsheet."
            )
            return
        }

        if (syncJob?.isActive == true) return

        syncJob = scope.launch {
            _syncStatus.value = _syncStatus.value.copy(state = SyncState.SYNCING)

            if (!isOnline()) {
                val pending = repository.getPendingSyncExpenses()
                _syncStatus.value = _syncStatus.value.copy(
                    state = SyncState.PENDING,
                    pendingCount = pending.size,
                    errorMessage = "Offline. Changes saved locally. Will sync when online."
                )
                return@launch
            }

            val token = getAccessToken()
            if (token.isNullOrBlank()) {
                val pending = repository.getPendingSyncExpenses()
                _syncStatus.value = _syncStatus.value.copy(
                    state = SyncState.FAILED,
                    pendingCount = pending.size,
                    errorMessage = "Authentication expired. Tap to reconnect Google account."
                )
                return@launch
            }

            val success = withContext(Dispatchers.IO) {
                try {
                    fullReconcileGoogleSheet(sheetId, token)
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }

            if (success) {
                val nowStr = Formatters.currentTimeString()
                saveLastSyncTime(nowStr)
                _syncStatus.value = _syncStatus.value.copy(
                    state = SyncState.SYNCED,
                    pendingCount = 0,
                    lastSyncTime = nowStr,
                    errorMessage = null
                )
            } else {
                val pending = repository.getPendingSyncExpenses()
                _syncStatus.value = _syncStatus.value.copy(
                    state = SyncState.FAILED,
                    pendingCount = pending.size,
                    errorMessage = "Sync failed. Will retry automatically."
                )
            }
        }
    }

    /**
     * Reorganizes and repairs a sheet tab's headers and column order so that the columns are:
     * Transaction ID | Date | Time | Type | Category | Amount | Note
     * "Note" is ALWAYS the last column. No existing data is lost.
     */
    fun checkAndReorganizeTabHeaders(
        spreadsheetId: String,
        accessToken: String,
        tabTitle: String
    ): Boolean {
        return try {
            val encodedTitle = URLEncoder.encode(tabTitle, "UTF-8")
            val getReq = Request.Builder()
                .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$encodedTitle!A:Z")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val getRes = client.newCall(getReq).execute()
            if (!getRes.isSuccessful) return false

            val json = JSONObject(getRes.body?.string() ?: "{}")
            val valuesArr = json.optJSONArray("values")

            if (valuesArr == null || valuesArr.length() == 0) {
                // Empty sheet: write standard headers
                val headerBody = JSONObject().apply {
                    put("values", JSONArray().apply {
                        val headerRow = JSONArray()
                        STANDARD_HEADERS.forEach { headerRow.put(it) }
                        put(headerRow)
                    })
                }.toString().toRequestBody("application/json".toMediaType())

                val writeReq = Request.Builder()
                    .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$encodedTitle!A1:G1?valueInputOption=USER_ENTERED")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .put(headerBody)
                    .build()

                val res = client.newCall(writeReq).execute()
                return res.isSuccessful
            }

            val firstRow = valuesArr.optJSONArray(0) ?: JSONArray()
            val currentHeaders = (0 until firstRow.length()).map { firstRow.optString(it, "").trim() }

            val isExactStandard = currentHeaders.size == STANDARD_HEADERS.size &&
                    currentHeaders.indices.all { currentHeaders[it].equals(STANDARD_HEADERS[it], ignoreCase = true) }

            if (isExactStandard) {
                return true
            }

            // Map each standard header to its column index in currentHeaders
            fun findCol(vararg keywords: String): Int {
                for (kw in keywords) {
                    val idx = currentHeaders.indexOfFirst { it.contains(kw, ignoreCase = true) }
                    if (idx >= 0) return idx
                }
                return -1
            }

            val idxTxnId = findCol("transaction", "txn", "id").takeIf { it >= 0 } ?: 0
            val idxDate = findCol("date").takeIf { it >= 0 } ?: 1
            val idxTime = findCol("time").takeIf { it >= 0 } ?: 2
            val idxType = findCol("type").takeIf { it >= 0 } ?: 3
            val idxCategory = findCol("cat").takeIf { it >= 0 } ?: 4
            val idxAmount = findCol("amount").takeIf { it >= 0 } ?: 5
            val idxNote = findCol("note", "desc").takeIf { it >= 0 } ?: 6

            val reorganizedGrid = JSONArray()
            val newHeaderRow = JSONArray()
            STANDARD_HEADERS.forEach { newHeaderRow.put(it) }
            reorganizedGrid.put(newHeaderRow)

            for (r in 1 until valuesArr.length()) {
                val oldRow = valuesArr.optJSONArray(r) ?: continue
                val row = JSONArray().apply {
                    put(oldRow.opt(idxTxnId) ?: "")
                    put(oldRow.opt(idxDate) ?: "")
                    put(oldRow.opt(idxTime) ?: "")
                    put(oldRow.opt(idxType) ?: "")
                    put(oldRow.opt(idxCategory) ?: "")
                    put(oldRow.opt(idxAmount) ?: "")
                    put(oldRow.opt(idxNote) ?: "")
                }
                reorganizedGrid.put(row)
            }

            val updateBody = JSONObject().apply {
                put("values", reorganizedGrid)
            }.toString().toRequestBody("application/json".toMediaType())

            val updateReq = Request.Builder()
                .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$encodedTitle!A1:G${valuesArr.length()}?valueInputOption=USER_ENTERED")
                .addHeader("Authorization", "Bearer $accessToken")
                .put(updateBody)
                .build()

            val updateRes = client.newCall(updateReq).execute()
            updateRes.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Performs full two-way reconciliation between local active app data and the Google Sheet.
     * Ensures:
     * - Every active transaction is present with its exact numeric Transaction ID.
     * - Existing rows are updated if modified.
     * - Obsolete or deleted rows in the sheet are removed.
     * - Duplicate rows are eliminated.
     * - Column order is strictly: Transaction ID | Date | Time | Type | Category | Amount | Note.
     */
    private suspend fun fullReconcileGoogleSheet(
        spreadsheetId: String,
        accessToken: String
    ): Boolean {
        return try {
            val activeExpenses = repository.getAllActiveExpensesList()
            val localTxnMap = activeExpenses.associateBy { it.transactionId }
            val localByMonth = activeExpenses.groupBy {
                val prefix = if (it.date.length >= 7) it.date.substring(0, 7) else "2026-10"
                Formatters.formatMonthYear(prefix)
            }

            // Step 1: Fetch spreadsheet metadata (tabs)
            val metaReq = Request.Builder()
                .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId?fields=sheets.properties(sheetId,title)")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val metaRes = client.newCall(metaReq).execute()
            if (!metaRes.isSuccessful) return false
            val metaJson = JSONObject(metaRes.body?.string() ?: "{}")
            val sheetsArr = metaJson.optJSONArray("sheets") ?: JSONArray()

            val existingTabTitles = mutableSetOf<String>()
            for (i in 0 until sheetsArr.length()) {
                val title = sheetsArr.getJSONObject(i).getJSONObject("properties").getString("title")
                existingTabTitles.add(title.lowercase())
            }

            val requiredMonths = if (localByMonth.isEmpty()) {
                listOf(Formatters.formatMonthYear(Formatters.currentMonthString()))
            } else {
                localByMonth.keys.toList()
            }

            // If spreadsheet only has "Sheet1" and it's empty, rename it to the first required month
            if (sheetsArr.length() == 1) {
                val onlySheet = sheetsArr.getJSONObject(0).getJSONObject("properties")
                val onlyTitle = onlySheet.getString("title")
                if (onlyTitle.equals("Sheet1", ignoreCase = true) && requiredMonths.isNotEmpty()) {
                    val firstMonth = requiredMonths.first()
                    val renameBody = JSONObject().apply {
                        put("requests", JSONArray().apply {
                            put(JSONObject().apply {
                                put("updateSheetProperties", JSONObject().apply {
                                    put("properties", JSONObject().apply {
                                        put("sheetId", onlySheet.getInt("sheetId"))
                                        put("title", firstMonth)
                                    })
                                    put("fields", "title")
                                })
                            })
                        })
                    }.toString().toRequestBody("application/json".toMediaType())

                    val renameReq = Request.Builder()
                        .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId:batchUpdate")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .post(renameBody)
                        .build()
                    val renameRes = client.newCall(renameReq).execute()
                    if (renameRes.isSuccessful) {
                        existingTabTitles.remove("sheet1")
                        existingTabTitles.add(firstMonth.lowercase())
                    }
                }
            }

            for (mTab in requiredMonths) {
                if (!existingTabTitles.contains(mTab.lowercase())) {
                    ensureMonthlyTabExists(spreadsheetId, accessToken, mTab)
                    existingTabTitles.add(mTab.lowercase())
                }
            }

            // Re-fetch sheet tabs to have up-to-date sheetIds and titles
            val metaReq2 = Request.Builder()
                .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId?fields=sheets.properties(sheetId,title)")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            val metaRes2 = client.newCall(metaReq2).execute()
            val metaJson2 = JSONObject(metaRes2.body?.string() ?: "{}")
            val currentSheetsArr = metaJson2.optJSONArray("sheets") ?: JSONArray()

            val matchedLocalTxnIds = mutableSetOf<String>()
            val rowsToDeleteBySheet = mutableMapOf<Int, MutableList<Int>>()
            val valueUpdates = JSONArray()

            // Step 2: Inspect and reconcile each tab in the spreadsheet
            for (i in 0 until currentSheetsArr.length()) {
                val sheetProp = currentSheetsArr.getJSONObject(i).getJSONObject("properties")
                val sId = sheetProp.getInt("sheetId")
                val sTitle = sheetProp.getString("title")

                // Check and reorganize headers if column order is not standard
                checkAndReorganizeTabHeaders(spreadsheetId, accessToken, sTitle)

                val encodedTitle = URLEncoder.encode(sTitle, "UTF-8")
                val valReq = Request.Builder()
                    .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$encodedTitle!A:G")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .get()
                    .build()

                val valRes = client.newCall(valReq).execute()
                if (!valRes.isSuccessful) continue
                val valJson = JSONObject(valRes.body?.string() ?: "{}")
                val rows = valJson.optJSONArray("values") ?: JSONArray()

                for (r in 1 until rows.length()) {
                    val row = rows.optJSONArray(r) ?: continue
                    val cellTxnId = row.optString(0, "").trim()

                    if (cellTxnId.isBlank()) {
                        rowsToDeleteBySheet.getOrPut(sId) { mutableListOf() }.add(r)
                        continue
                    }

                    val exp = localTxnMap[cellTxnId]
                    if (exp == null) {
                        // Obsolete or deleted row: does not exist in local active transactions
                        rowsToDeleteBySheet.getOrPut(sId) { mutableListOf() }.add(r)
                    } else {
                        if (matchedLocalTxnIds.contains(cellTxnId)) {
                            // Duplicate row in spreadsheet -> delete duplicate
                            rowsToDeleteBySheet.getOrPut(sId) { mutableListOf() }.add(r)
                        } else {
                            matchedLocalTxnIds.add(cellTxnId)
                            // Compare row data with local active expense
                            val sDate = row.optString(1, "").trim()
                            val sTime = row.optString(2, "").trim()
                            val sType = row.optString(3, "").trim()
                            val sCat = row.optString(4, "").trim()
                            val sAmtStr = row.optString(5, "").trim().replace("$", "").replace(",", "")
                            val sAmt = sAmtStr.toDoubleOrNull()
                            val sNote = row.optString(6, "").trim()

                            val expType = if (exp.transactionType == "CREDIT") "Credit" else "Expense"
                            val expAmt = exp.amount

                            val isMatch = sDate == exp.date &&
                                    sTime == exp.time &&
                                    sType.equals(expType, ignoreCase = true) &&
                                    sCat.equals(exp.category, ignoreCase = true) &&
                                    sAmt != null && Math.abs(sAmt - expAmt) < 0.001 &&
                                    sNote == exp.note

                            if (!isMatch) {
                                val updatedRow = JSONArray().apply {
                                    put(exp.transactionId)
                                    put(exp.date)
                                    put(exp.time)
                                    put(expType)
                                    put(exp.category)
                                    put(exp.amount)
                                    put(exp.note)
                                }
                                val rowNum = r + 1
                                valueUpdates.put(JSONObject().apply {
                                    put("range", "$sTitle!A$rowNum:G$rowNum")
                                    put("values", JSONArray().apply { put(updatedRow) })
                                })
                            }
                        }
                    }
                }
            }

            // Step 3: Execute row deletions in descending index order per sheet
            if (rowsToDeleteBySheet.isNotEmpty()) {
                val deleteRequests = JSONArray()
                for ((sId, indices) in rowsToDeleteBySheet) {
                    val descIndices = indices.distinct().sortedDescending()
                    for (rowIdx in descIndices) {
                        deleteRequests.put(JSONObject().apply {
                            put("deleteDimension", JSONObject().apply {
                                put("range", JSONObject().apply {
                                    put("sheetId", sId)
                                    put("dimension", "ROWS")
                                    put("startIndex", rowIdx)
                                    put("endIndex", rowIdx + 1)
                                })
                            })
                        })
                    }
                }
                if (deleteRequests.length() > 0) {
                    val batchDelBody = JSONObject().apply {
                        put("requests", deleteRequests)
                    }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

                    val delReq = Request.Builder()
                        .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId:batchUpdate")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .post(batchDelBody)
                        .build()
                    client.newCall(delReq).execute()
                }
            }

            // Step 4: Execute row updates
            if (valueUpdates.length() > 0) {
                val batchUpdateBody = JSONObject().apply {
                    put("valueInputOption", "USER_ENTERED")
                    put("data", valueUpdates)
                }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

                val updateReq = Request.Builder()
                    .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values:batchUpdate")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post(batchUpdateBody)
                    .build()
                client.newCall(updateReq).execute()
            }

            // Step 5: Append missing active local transactions to their month tabs
            val missingExpenses = activeExpenses.filter { it.transactionId !in matchedLocalTxnIds }
            if (missingExpenses.isNotEmpty()) {
                val missingByMonth = missingExpenses.groupBy {
                    val prefix = if (it.date.length >= 7) it.date.substring(0, 7) else "2026-10"
                    Formatters.formatMonthYear(prefix)
                }
                for ((mTab, items) in missingByMonth) {
                    ensureMonthlyTabExists(spreadsheetId, accessToken, mTab)
                    val encodedTab = URLEncoder.encode(mTab, "UTF-8")
                    val rowsToAppend = JSONArray()
                    for (item in items) {
                        val row = JSONArray().apply {
                            put(item.transactionId)
                            put(item.date)
                            put(item.time)
                            put(if (item.transactionType == "CREDIT") "Credit" else "Expense")
                            put(item.category)
                            put(item.amount)
                            put(item.note)
                        }
                        rowsToAppend.put(row)
                    }
                    val appendBody = JSONObject().apply {
                        put("values", rowsToAppend)
                    }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

                    val appendReq = Request.Builder()
                        .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$encodedTab!A:G:append?valueInputOption=USER_ENTERED")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .post(appendBody)
                        .build()
                    client.newCall(appendReq).execute()
                }
            }

            // Step 6: Mark all active local expenses as SYNCED
            if (activeExpenses.isNotEmpty()) {
                repository.markExpensesSynced(activeExpenses.map { it.id })
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

                    val encodedTab = URLEncoder.encode(monthTab, "UTF-8")
                    val headerBody = JSONObject().apply {
                        put("values", JSONArray().apply {
                            val headerRow = JSONArray()
                            STANDARD_HEADERS.forEach { headerRow.put(it) }
                            put(headerRow)
                        })
                    }.toString().toRequestBody("application/json".toMediaType())

                    val headerReq = Request.Builder()
                        .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$encodedTab!A1:G1:append?valueInputOption=USER_ENTERED")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .post(headerBody)
                        .build()

                    client.newCall(headerReq).execute()
                } else {
                    // Tab exists: verify and repair headers/column order
                    checkAndReorganizeTabHeaders(spreadsheetId, accessToken, monthTab)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    data class FoundRowInfo(
        val sheetIdInt: Int,
        val rowIndex: Int, // 0-based row index in Google Sheet
        val tabTitle: String
    )

    /**
     * Finds the exact Google Sheet row for a given transaction by searching Column A
     * for its unique Transaction ID (or fallback numeric ID for legacy entries).
     * Returns FoundRowInfo(sheetIdInt, rowIndex, tabTitle) or null if not found.
     */
    suspend fun findTransactionRow(expense: Expense): FoundRowInfo? = withContext(Dispatchers.IO) {
        val sheetId = _syncStatus.value.spreadsheetId ?: return@withContext null
        val token = getAccessToken() ?: return@withContext null

        val targetTxnId = expense.transactionId.trim()
        val targetOldId = expense.id.toString()
        val targetAltId = "TXN-${expense.id}"

        // Fetch sheet tabs metadata
        val metaReq = Request.Builder()
            .url("https://sheets.googleapis.com/v4/spreadsheets/$sheetId?fields=sheets.properties(sheetId,title)")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        val metaRes = client.newCall(metaReq).execute()
        if (!metaRes.isSuccessful) {
            throw Exception("Failed to fetch spreadsheet metadata (HTTP ${metaRes.code})")
        }

        val metaJson = JSONObject(metaRes.body?.string() ?: "{}")
        val sheetsArr = metaJson.optJSONArray("sheets") ?: JSONArray()
        if (sheetsArr.length() == 0) return@withContext null

        val expectedMonthTab = if (expense.date.length >= 7) {
            Formatters.formatMonthYear(expense.date.substring(0, 7))
        } else null

        // Order tabs: expected month tab first, then the rest
        val orderedSheets = mutableListOf<JSONObject>()
        for (i in 0 until sheetsArr.length()) {
            val sheetObj = sheetsArr.getJSONObject(i).getJSONObject("properties")
            val title = sheetObj.getString("title")
            if (expectedMonthTab != null && title.equals(expectedMonthTab, ignoreCase = true)) {
                orderedSheets.add(0, sheetObj)
            } else {
                orderedSheets.add(sheetObj)
            }
        }

        for (sheetObj in orderedSheets) {
            val sId = sheetObj.getInt("sheetId")
            val sTitle = sheetObj.getString("title")
            val encodedTitle = URLEncoder.encode(sTitle, "UTF-8")

            val colAReq = Request.Builder()
                .url("https://sheets.googleapis.com/v4/spreadsheets/$sheetId/values/$encodedTitle!A:A")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val colARes = client.newCall(colAReq).execute()
            if (!colARes.isSuccessful) continue

            val colAJson = JSONObject(colARes.body?.string() ?: "{}")
            val valuesArr = colAJson.optJSONArray("values") ?: JSONArray()

            // rowIndex is 0-based in Google Sheet (row 0 is header row 1)
            for (rowIndex in 0 until valuesArr.length()) {
                val rowArr = valuesArr.optJSONArray(rowIndex) ?: continue
                if (rowArr.length() > 0) {
                    val cellVal = rowArr.optString(0, "").trim()
                    val matchesTxnId = targetTxnId.isNotEmpty() && cellVal.equals(targetTxnId, ignoreCase = true)
                    val matchesOldId = cellVal == targetOldId || cellVal.equals(targetAltId, ignoreCase = true)
                    if (matchesTxnId || matchesOldId) {
                        return@withContext FoundRowInfo(
                            sheetIdInt = sId,
                            rowIndex = rowIndex,
                            tabTitle = sTitle
                        )
                    }
                }
            }
        }

        null
    }

    /**
     * Deletes the exact row matching the transaction from the connected Google Sheet.
     * Uses deleteDimension API to ensure precise row deletion by unique Transaction ID.
     */
    suspend fun deleteTransactionFromSheet(expense: Expense): Result<Unit> = withContext(Dispatchers.IO) {
        val sheetId = _syncStatus.value.spreadsheetId
            ?: return@withContext Result.failure(Exception("No connected spreadsheet found."))

        if (!isOnline()) {
            return@withContext Result.failure(Exception("Network is offline. Cannot communicate with Google Sheets."))
        }

        val token = getAccessToken()
            ?: return@withContext Result.failure(Exception("Authentication token expired or unavailable."))

        try {
            val foundRow = findTransactionRow(expense)
            if (foundRow == null) {
                // If not found in any tab, the row is already deleted/absent in Google Sheets.
                return@withContext Result.success(Unit)
            }

            // Execute deleteDimension for exact row: [startIndex, endIndex)
            val batchBody = JSONObject().apply {
                val requests = JSONArray().apply {
                    put(JSONObject().apply {
                        put("deleteDimension", JSONObject().apply {
                            put("range", JSONObject().apply {
                                put("sheetId", foundRow.sheetIdInt)
                                put("dimension", "ROWS")
                                put("startIndex", foundRow.rowIndex)
                                put("endIndex", foundRow.rowIndex + 1)
                            })
                        })
                    })
                }
                put("requests", requests)
            }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val deleteReq = Request.Builder()
                .url("https://sheets.googleapis.com/v4/spreadsheets/$sheetId:batchUpdate")
                .addHeader("Authorization", "Bearer $token")
                .post(batchBody)
                .build()

            val deleteRes = client.newCall(deleteReq).execute()
            if (deleteRes.isSuccessful) {
                Result.success(Unit)
            } else {
                val errBody = deleteRes.body?.string() ?: ""
                Result.failure(Exception("Google Sheets API delete error (${deleteRes.code}): $errBody"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Prepares for future editing by updating an existing row in Google Sheets
     * without creating duplicates.
     */
    suspend fun updateTransactionInSheet(expense: Expense): Result<Unit> = withContext(Dispatchers.IO) {
        val sheetId = _syncStatus.value.spreadsheetId
            ?: return@withContext Result.failure(Exception("No connected spreadsheet found."))

        if (!isOnline()) {
            return@withContext Result.failure(Exception("Network is offline."))
        }

        val token = getAccessToken()
            ?: return@withContext Result.failure(Exception("Authentication token expired."))

        try {
            val foundRow = findTransactionRow(expense)
                ?: return@withContext Result.failure(Exception("Transaction row not found in Google Sheets."))

            val encodedTab = URLEncoder.encode(foundRow.tabTitle, "UTF-8")
            val rowNumber = foundRow.rowIndex + 1 // 1-based row number for A1 notation

            val txnId = if (expense.transactionId.isNotBlank()) expense.transactionId else expense.id.toString()
            val updateBody = JSONObject().apply {
                put("values", JSONArray().apply {
                    put(JSONArray().apply {
                        put(txnId)
                        put(expense.date)
                        put(expense.time)
                        put(if (expense.transactionType == "CREDIT") "Credit" else "Expense")
                        put(expense.category)
                        put(expense.amount)
                        put(expense.note)
                    })
                })
            }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val updateReq = Request.Builder()
                .url("https://sheets.googleapis.com/v4/spreadsheets/$sheetId/values/$encodedTab!A$rowNumber:G$rowNumber?valueInputOption=USER_ENTERED")
                .addHeader("Authorization", "Bearer $token")
                .put(updateBody)
                .build()

            val updateRes = client.newCall(updateReq).execute()
            if (updateRes.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Update row failed (HTTP ${updateRes.code})"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun saveLastSyncTime(time: String) {
        val activeEmail = _syncStatus.value.accountEmail ?: return
        prefs.edit().putString("last_sync_$activeEmail", time).apply()
    }
}
