package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sync.GoogleSheetsSyncManager
import com.example.sync.SyncState
import com.example.ui.ExpenseViewModel

@Composable
fun SyncSettingsDialog(
    viewModel: ExpenseViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val syncManager = viewModel.syncManager

    var showConnectDialog by remember { mutableStateOf(false) }
    var showChangeSpreadsheetDialog by remember { mutableStateOf(false) }
    var showDisconnectConfirmDialog by remember { mutableStateOf(false) }

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
                            text = "Account Status",
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
                                text = "Connect your personal Google account to sync expenses to your own dedicated spreadsheet.",
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
                                    text = "Connected as: ${syncStatus.accountEmail}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    ),
                                    color = Color(0xFF065F46),
                                    modifier = Modifier.testTag("connected_email_status_text")
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // If NOT CONNECTED: show Connect Google Sheets button
                if (syncStatus.state == SyncState.NOT_CONNECTED) {
                    Button(
                        onClick = { showConnectDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("connect_google_sheets_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
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
                } else {
                    // CONNECTED VIEW: Show spreadsheet details and actions
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("user_spreadsheet_card"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Selected Spreadsheet",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = syncStatus.spreadsheetTitle ?: GoogleSheetsSyncManager.DEFAULT_SPREADSHEET_TITLE,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("spreadsheet_name_text")
                            )
                            if (!syncStatus.spreadsheetId.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "ID: ${syncStatus.spreadsheetId}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Sync State Card
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
                            onClick = { showChangeSpreadsheetDialog = true },
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

                // Multi-User Security & Setup Guide
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Multi-User Isolation & Security",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            TextButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val script = GoogleSheetsSyncManager.getAppsScriptTemplate(syncStatus.spreadsheetId ?: "YOUR_SHEET_ID")
                                    val clip = ClipData.newPlainText("Apps Script", script)
                                    cm.setPrimaryClip(clip)
                                    Toast.makeText(context, "Apps Script template copied!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("copy_script_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy Script", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "• Each user syncs only to their own Google Account & Spreadsheet.\n" +
                                    "• Never uses shared credentials or personal spreadsheets.\n" +
                                    "• Auto-creates monthly tabs (e.g. October 2026, November 2026) with Monthly Totals.\n" +
                                    "• Disconnecting removes account credentials and stops sync while preserving local records.",
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Connect Google Account Dialog
    if (showConnectDialog) {
        var emailInput by remember { mutableStateOf("") }
        var sheetTitleInput by remember { mutableStateOf(GoogleSheetsSyncManager.DEFAULT_SPREADSHEET_TITLE) }
        var optionalWebhookInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showConnectDialog = false },
            title = {
                Text(
                    text = "Connect Google Account",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Sign in with your Google Account to automatically create or link your personal 'My Expenses' spreadsheet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("connect_email_input"),
                        label = { Text("Your Google Account Email") },
                        placeholder = { Text("e.g. you@gmail.com") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = sheetTitleInput,
                        onValueChange = { sheetTitleInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("connect_sheet_title_input"),
                        label = { Text("Spreadsheet Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = optionalWebhookInput,
                        onValueChange = { optionalWebhookInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("connect_webhook_input"),
                        label = { Text("Apps Script Web App URL (Optional)") },
                        placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (emailInput.isNotBlank()) {
                            syncManager.connectAccount(
                                email = emailInput,
                                spreadsheetTitle = sheetTitleInput.ifBlank { GoogleSheetsSyncManager.DEFAULT_SPREADSHEET_TITLE }
                            )
                            if (optionalWebhookInput.isNotBlank()) {
                                syncManager.setWebhookUrl(optionalWebhookInput)
                            }
                            showConnectDialog = false
                            Toast.makeText(context, "Connected as $emailInput", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("confirm_connect_btn")
                ) {
                    Text("Connect", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConnectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Change Spreadsheet Dialog
    if (showChangeSpreadsheetDialog) {
        var newTitleInput by remember { mutableStateOf(syncStatus.spreadsheetTitle ?: GoogleSheetsSyncManager.DEFAULT_SPREADSHEET_TITLE) }
        var newSheetIdInput by remember { mutableStateOf(syncStatus.spreadsheetId ?: "") }

        AlertDialog(
            onDismissRequest = { showChangeSpreadsheetDialog = false },
            title = {
                Text(
                    text = "Change Spreadsheet",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column {
                    Text(
                        text = "Select or create a spreadsheet for your account (${syncStatus.accountEmail}).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newTitleInput,
                        onValueChange = { newTitleInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("change_sheet_title_input"),
                        label = { Text("Spreadsheet Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = newSheetIdInput,
                        onValueChange = { newSheetIdInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("change_sheet_id_input"),
                        label = { Text("Spreadsheet ID") },
                        placeholder = { Text("Enter Google Sheet ID") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalId = if (newSheetIdInput.isNotBlank()) newSheetIdInput else "user_sheet_${System.currentTimeMillis()}"
                        val finalTitle = newTitleInput.ifBlank { GoogleSheetsSyncManager.DEFAULT_SPREADSHEET_TITLE }
                        syncManager.setSpreadsheet(finalId, finalTitle)
                        showChangeSpreadsheetDialog = false
                        Toast.makeText(context, "Spreadsheet updated to $finalTitle", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("save_spreadsheet_change_btn")
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangeSpreadsheetDialog = false }) {
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
