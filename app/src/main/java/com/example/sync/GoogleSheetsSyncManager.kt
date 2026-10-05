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

        const val DEFAULT_SPREADSHEET_TITLE = "My Expenses"

        const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
        const val SCOPE_SPREADSHEETS = "https://www.googleapis.com/auth/spreadsheets"
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
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        val activeEmail = prefs.getString(KEY_ACTIVE_USER, null)

        // Only restore connected state if a REAL Google Account is recognized
        if (lastAccount != null && !activeEmail.isNullOrBlank() && lastAccount.email.equals(activeEmail, ignoreCase = true)) {
            val sheetId = prefs.getString("sheet_id_$activeEmail", null)
            val sheetTitle = prefs.getString("sheet_title_$activeEmail", DEFAULT_SPREADSHEET_TITLE)
            val lastSync = prefs.getString("last_sync_$activeEmail", null)
            return SyncStatus(
                state = if (!sheetId.isNullOrBlank()) SyncState.SYNCED else SyncState.PENDING,
                accountEmail = activeEmail,
                spreadsheetId = sheetId,
                spreadsheetTitle = sheetTitle,
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

            _syncStatus.value = SyncStatus(
                state = if (existingSheetId != null) SyncState.SYNCED else SyncState.PENDING,
                accountEmail = cleanEmail,
                spreadsheetId = existingSheetId,
                spreadsheetTitle = existingTitle,
                errorMessage = null
            )

            checkPendingCount()
            onSuccess(account)
        } catch (e: ApiException) {
            val detailedMsg = when (e.statusCode) {
                10 -> "Developer error (Code 10): Ensure your Android OAuth Client ID in Google Cloud Console is configured with package name 'com.aistudio.myexpenses.kfqrz' and the correct SHA-1 fingerprint."
                7 -> "Network error (Code 7): Please check your internet connection."
                12501 -> "Google Sign-In was cancelled."
                12500 -> "Google Sign-In failed (Code 12500): Check Google Play Services."
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
            ?: return@withContext Result.failure(Exception("Unable to acquire Google OAuth token. Please ensure Google Cloud Console OAuth consent screen is configured."))

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
                setSpreadsheet(newSheetId, finalTitle)
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
     * Verifies that the authenticated user has access to an existing spreadsheet and connects it.
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
                .url("https://sheets.googleapis.com/v4/spreadsheets/$cleanId?fields=properties.title")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val resJson = JSONObject(response.body?.string() ?: "{}")
                val title = resJson.optJSONObject("properties")?.optString("title") ?: "Existing Spreadsheet"
                setSpreadsheet(cleanId, title)
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
            pendingCount = 0,
            lastSyncTime = null,
            errorMessage = null
        )
    }

    /**
     * Stores the selected spreadsheet for the active user.
     */
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

            val pendingExpenses = repository.getPendingSyncExpenses()
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

            val token = getAccessToken()
            if (token.isNullOrBlank()) {
                _syncStatus.value = _syncStatus.value.copy(
                    state = SyncState.FAILED,
                    pendingCount = pendingExpenses.size,
                    errorMessage = "Authentication expired. Tap to reconnect Google account."
                )
                return@launch
            }

            val success = withContext(Dispatchers.IO) {
                try {
                    syncViaGoogleSheetsApi(sheetId, token, pendingExpenses)
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
                _syncStatus.value = _syncStatus.value.copy(
                    state = SyncState.FAILED,
                    pendingCount = pendingExpenses.size,
                    errorMessage = "Sync failed. Will retry automatically."
                )
            }
        }
    }

    private fun syncViaGoogleSheetsApi(
        spreadsheetId: String,
        accessToken: String,
        expenses: List<Expense>
    ): Boolean {
        return try {
            val monthlyGroups = expenses.groupBy { exp ->
                val monthPrefix = if (exp.date.length >= 7) exp.date.substring(0, 7) else "2026-10"
                Formatters.formatMonthYear(monthPrefix)
            }

            for ((monthTab, items) in monthlyGroups) {
                ensureMonthlyTabExists(spreadsheetId, accessToken, monthTab)

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
