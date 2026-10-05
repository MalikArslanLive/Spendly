package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sync.GoogleSheetItem
import com.example.sync.GoogleSheetsSyncManager
import com.example.sync.SyncState
import com.example.ui.ExpenseViewModel
import kotlinx.coroutines.launch

fun extractSpreadsheetIdFromUrl(input: String): String {
    val trimmed = input.trim()
    val regex = Regex("/spreadsheets/d/([a-zA-Z0-9-_]+)")
    val match = regex.find(trimmed)
    return match?.groupValues?.get(1) ?: trimmed
}

@Composable
fun SyncSettingsDialog(
    viewModel: ExpenseViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val syncManager = viewModel.syncManager

    var showSheetsSetupDialog by remember { mutableStateOf(false) }
    var showDisconnectConfirmDialog by remember { mutableStateOf(false) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    var isSigningIn by remember { mutableStateOf(false) }

    // Google Sign-In Activity Result Launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isSigningIn = false
        if (result.resultCode == Activity.RESULT_OK) {
            syncManager.handleGoogleSignInResult(
                intent = result.data,
                onSuccess = { account ->
                    authErrorMessage = null
                    Toast.makeText(context, "Authenticated as ${account.email}", Toast.LENGTH_SHORT).show()
                    // Only show setup modal if there is no linked spreadsheet yet!
                    if (syncStatus.spreadsheetId.isNullOrBlank()) {
                        showSheetsSetupDialog = true
                    }
                },
                onError = { error ->
                    authErrorMessage = error
                }
            )
        } else {
            syncManager.handleGoogleSignInResult(
                intent = result.data,
                onSuccess = {},
                onError = { error ->
                    authErrorMessage = error
                }
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(24.dp))
                .testTag("sync_settings_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TableChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Google Sheets Sync",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_sync_dialog_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Account Connection Status Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_status_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (syncStatus.state == SyncState.NOT_CONNECTED)
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        else
                            Color(0xFFECFDF5)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Google Account Status",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (syncStatus.state == SyncState.NOT_CONNECTED) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color.Gray)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Not Connected",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("not_connected_status_text")
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Sign into your own Google Account to sync transactions to your private Google Spreadsheet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF059669))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Google Sheets Connected ✅",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    ),
                                    color = Color(0xFF065F46),
                                    modifier = Modifier.testTag("connected_email_status_text")
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Connected as: ${syncStatus.accountEmail}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = Color(0xFF065F46)
                            )
                        }
                    }
                }

                if (authErrorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = authErrorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF991B1B)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // If NOT CONNECTED: show Connect Google Sheets button
                if (syncStatus.state == SyncState.NOT_CONNECTED) {
                    Button(
                        onClick = {
                            isSigningIn = true
                            authErrorMessage = null
                            try {
                                googleSignInLauncher.launch(syncManager.getGoogleSignInIntent())
                            } catch (e: Exception) {
                                isSigningIn = false
                                authErrorMessage = "Unable to launch Google Sign-In: ${e.message}"
                            }
                        },
                        enabled = !isSigningIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("connect_google_sheets_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isSigningIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connecting with Google...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Connect Google Sheets",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                } else {
                    // CONNECTED VIEW: Show currently linked spreadsheet details clearly
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("user_spreadsheet_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Connected Spreadsheet",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                val typeLabel = if (syncStatus.spreadsheetType == "created") "Created by App" else "Existing Spreadsheet"
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = typeLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "📊",
                                    fontSize = 18.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = syncStatus.spreadsheetTitle ?: "Spendly",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("spreadsheet_name_text")
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // [Open Spreadsheet →] Button
                            val spreadsheetUrl = syncStatus.spreadsheetUrl
                                ?: if (!syncStatus.spreadsheetId.isNullOrBlank()) "https://docs.google.com/spreadsheets/d/${syncStatus.spreadsheetId}/edit" else null

                            if (spreadsheetUrl != null) {
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(spreadsheetUrl))
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("open_spreadsheet_btn"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "Open Spreadsheet →", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Sync Status Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when (syncStatus.state) {
                                SyncState.SYNCED -> Color(0xFFECFDF5)
                                SyncState.PENDING -> Color(0xFFFEF3C7)
                                SyncState.SYNCING -> Color(0xFFEFF6FF)
                                SyncState.FAILED -> Color(0xFFFEE2E2)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                when (syncStatus.state) {
                                    SyncState.SYNCED -> Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF059669),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    SyncState.PENDING -> Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    SyncState.SYNCING -> CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    SyncState.FAILED -> Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    else -> {}
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    val statusTitle = when (syncStatus.state) {
                                        SyncState.SYNCED -> "✓ Synced"
                                        SyncState.PENDING -> "⟳ Sync Pending (${syncStatus.pendingCount})"
                                        SyncState.SYNCING -> "Syncing..."
                                        SyncState.FAILED -> "⚠ Sync Failed"
                                        else -> "Not Connected"
                                    }
                                    Text(
                                        text = statusTitle,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = when (syncStatus.state) {
                                            SyncState.SYNCED -> Color(0xFF065F46)
                                            SyncState.PENDING -> Color(0xFF92400E)
                                            SyncState.SYNCING -> Color(0xFF1E40AF)
                                            SyncState.FAILED -> Color(0xFF991B1B)
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.testTag("sync_state_label")
                                    )
                                    if (syncStatus.lastSyncTime != null) {
                                        Text(
                                            text = "Last synced: ${syncStatus.lastSyncTime}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else if (syncStatus.errorMessage != null) {
                                        Text(
                                            text = syncStatus.errorMessage ?: "",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = { viewModel.syncNow() },
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("sync_now_dialog_button"),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Sync Now", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Buttons: [Change Spreadsheet] & [Disconnect Google Account]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showSheetsSetupDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("change_spreadsheet_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Change Sheet", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = { showDisconnectConfirmDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("disconnect_account_btn"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.LinkOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Disconnect", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Privacy Note
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Privacy & Multi-User Isolation",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• Each user authenticates their own Google Account.\n" +
                                    "• Only your account's spreadsheet is ever modified.\n" +
                                    "• Monthly tabs (e.g. October 2026) are managed automatically.\n" +
                                    "• Both Expenses and Credits are synced with a dedicated Type column.\n" +
                                    "• Disconnecting removes credentials and stops sync while preserving local records.",
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Google Sheets Setup Modal (Create New Spreadsheet OR Use Existing Spreadsheet)
    if (showSheetsSetupDialog) {
        var selectedTopTab by remember { mutableIntStateOf(0) } // 0: Create New, 1: Use Existing
        var existingSubTab by remember { mutableIntStateOf(0) } // 0: Paste Link, 1: Search Google Sheets

        var newSheetTitle by remember { mutableStateOf(GoogleSheetsSyncManager.DEFAULT_SPREADSHEET_TITLE) }
        var existingSheetUrl by remember { mutableStateOf("") }
        var searchQuery by remember { mutableStateOf("") }
        var searchResults by remember { mutableStateOf<List<GoogleSheetItem>>(emptyList()) }
        var isSearching by remember { mutableStateOf(false) }

        var isProcessing by remember { mutableStateOf(false) }
        var setupErrorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                if (!isProcessing) showSheetsSetupDialog = false
            },
            title = {
                Text(
                    text = "Google Sheets Setup",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    // Top Tabs: Create New vs Use Existing
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedTopTab == 0,
                            onClick = { selectedTopTab = 0; setupErrorMessage = null },
                            label = { Text("Create New") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("tab_create_new_sheet")
                        )

                        FilterChip(
                            selected = selectedTopTab == 1,
                            onClick = { selectedTopTab = 1; setupErrorMessage = null },
                            label = { Text("Use Existing") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("tab_use_existing_sheet")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (selectedTopTab == 0) {
                        // Create New Spreadsheet
                        Text(
                            text = "A dedicated spreadsheet will be created in your Google Drive with monthly expense tabs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = newSheetTitle,
                            onValueChange = { newSheetTitle = it },
                            label = { Text("Spreadsheet Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_new_sheet_title_input")
                        )
                    } else {
                        // Use Existing: Sub-tabs (Option A: Paste Link, Option B: Search Drive)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = existingSubTab == 0,
                                onClick = { existingSubTab = 0; setupErrorMessage = null },
                                label = { Text("Option A: Paste Link", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = existingSubTab == 1,
                                onClick = {
                                    existingSubTab = 1
                                    setupErrorMessage = null
                                    if (searchResults.isEmpty()) {
                                        isSearching = true
                                        scope.launch {
                                            val res = syncManager.searchUserGoogleSheets("")
                                            isSearching = false
                                            res.onSuccess { searchResults = it }
                                                .onFailure { setupErrorMessage = it.message }
                                        }
                                    }
                                },
                                label = { Text("Option B: Search Sheets", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (existingSubTab == 0) {
                            // Option A: Paste Link
                            Text(
                                text = "Paste the complete Google Spreadsheet link. The app will extract the ID and retrieve the spreadsheet title.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = existingSheetUrl,
                                onValueChange = { existingSheetUrl = it },
                                label = { Text("Paste Google Sheets URL") },
                                placeholder = { Text("https://docs.google.com/spreadsheets/d/.../edit") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("setup_existing_sheet_url_input")
                            )
                        } else {
                            // Option B: Search Drive
                            Text(
                                text = "Search and select from your accessible Google Sheets in Google Drive:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    label = { Text("Search by name") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        isSearching = true
                                        scope.launch {
                                            val res = syncManager.searchUserGoogleSheets(searchQuery)
                                            isSearching = false
                                            res.onSuccess { searchResults = it }
                                                .onFailure { setupErrorMessage = it.message }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (isSearching) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Searching Google Drive...", style = MaterialTheme.typography.bodySmall)
                                }
                            } else if (searchResults.isEmpty()) {
                                Text(
                                    text = "No spreadsheets found. Try a different name or paste a link.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    searchResults.forEach { sheet ->
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                                .clickable {
                                                    isProcessing = true
                                                    setupErrorMessage = null
                                                    scope.launch {
                                                        val res = syncManager.verifyAndConnectExistingSpreadsheet(sheet.id)
                                                        isProcessing = false
                                                        res.onSuccess { title ->
                                                            Toast.makeText(context, "Linked: $title", Toast.LENGTH_SHORT).show()
                                                            showSheetsSetupDialog = false
                                                        }.onFailure { err ->
                                                            setupErrorMessage = err.message
                                                        }
                                                    }
                                                },
                                            color = MaterialTheme.colorScheme.surface
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Description,
                                                    contentDescription = null,
                                                    tint = Color(0xFF0F9D58),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "📊 ${sheet.name}",
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (setupErrorMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = setupErrorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (isProcessing) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (selectedTopTab == 0) "Creating spreadsheet in Google Drive..." else "Linking spreadsheet...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                // When in paste-link or create-new mode:
                if (selectedTopTab == 0 || (selectedTopTab == 1 && existingSubTab == 0)) {
                    Button(
                        onClick = {
                            isProcessing = true
                            setupErrorMessage = null
                            scope.launch {
                                if (selectedTopTab == 0) {
                                    val result = syncManager.createSpreadsheetInUserDrive(newSheetTitle)
                                    isProcessing = false
                                    result.onSuccess {
                                        Toast.makeText(context, "Spreadsheet created successfully!", Toast.LENGTH_SHORT).show()
                                        showSheetsSetupDialog = false
                                    }.onFailure { error ->
                                        setupErrorMessage = error.message ?: "Failed to create spreadsheet"
                                    }
                                } else {
                                    val id = extractSpreadsheetIdFromUrl(existingSheetUrl)
                                    val result = syncManager.verifyAndConnectExistingSpreadsheet(id)
                                    isProcessing = false
                                    result.onSuccess { title ->
                                        Toast.makeText(context, "Connected to \"$title\"", Toast.LENGTH_SHORT).show()
                                        showSheetsSetupDialog = false
                                    }.onFailure { error ->
                                        setupErrorMessage = error.message ?: "Failed to verify spreadsheet access"
                                    }
                                }
                            }
                        },
                        enabled = !isProcessing && (selectedTopTab == 0 || existingSheetUrl.isNotBlank()),
                        modifier = Modifier.testTag("confirm_sheets_setup_btn")
                    ) {
                        Text(
                            text = if (selectedTopTab == 0) "Create Spreadsheet" else "Link Spreadsheet",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSheetsSetupDialog = false },
                    enabled = !isProcessing
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Disconnect Confirmation Dialog
    if (showDisconnectConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.LinkOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Disconnect Google Account?",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column {
                    Text(
                        text = "• Local expenses will remain on this device.\n• Google Sheets synchronization will stop.\n• Existing data in Google Sheets will NOT automatically be deleted.",
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        syncManager.disconnectAccount()
                        showDisconnectConfirmDialog = false
                        Toast.makeText(context, "Google account disconnected", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_disconnect_btn")
                ) {
                    Text("Disconnect", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDisconnectConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_disconnect_btn")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
