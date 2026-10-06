package com.example.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.Expense
import com.example.util.Formatters

@Composable
fun DeleteConfirmDialog(
    expense: Expense,
    isSynced: Boolean = false,
    isDeleting: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val amountFormatted = Formatters.formatPkr(expense.amount)

    val titleText = if (isSynced) {
        "Delete Transaction?"
    } else if (expense.note.isNotBlank()) {
        "Delete \"${expense.note}\"?"
    } else {
        "Delete ${expense.category}?"
    }

    val bodyText = if (isSynced) {
        "This transaction has already been synced to Google Sheets.\nMoving it to Trash will remove its row from the connected Google Sheet. You can restore it anytime from Trash."
    } else {
        val typeLabel = if (expense.transactionType == "CREDIT") "credit" else "expense"
        "Move this $typeLabel of $amountFormatted on ${expense.date} to Trash? You can restore it anytime from Trash."
    }

    AlertDialog(
        onDismissRequest = {
            if (!isDeleting) onDismiss()
        },
        icon = {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isDeleting,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.testTag("confirm_delete_button")
            ) {
                if (isDeleting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Deleting...", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting,
                modifier = Modifier.testTag("cancel_delete_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun GoogleSheetDeleteErrorDialog(
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Could not update Google Sheets",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Text(
                text = "The transaction could not be removed from the connected Google Sheet. Your local transaction has been kept so nothing is lost.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.testTag("retry_delete_button")
            ) {
                Text("Retry", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag("cancel_error_delete_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

