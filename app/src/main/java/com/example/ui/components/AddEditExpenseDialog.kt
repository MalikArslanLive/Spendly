package com.example.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.Expense
import com.example.model.ExpenseCategories
import com.example.util.Formatters
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditExpenseDialog(
    initialExpense: Expense?,
    defaultDate: String,
    onDismiss: () -> Unit,
    onSave: (id: Long, amount: Double, category: String, note: String, date: String, time: String) -> Unit,
    onDelete: ((Expense) -> Unit)? = null
) {
    val context = LocalContext.current
    val isEdit = initialExpense != null

    var amountText by remember {
        mutableStateOf(
            if (initialExpense != null) {
                if (initialExpense.amount % 1.0 == 0.0) {
                    initialExpense.amount.toLong().toString()
                } else {
                    initialExpense.amount.toString()
                }
            } else ""
        )
    }

    var selectedCategory by remember {
        mutableStateOf(initialExpense?.category ?: "Food")
    }

    var noteText by remember {
        mutableStateOf(initialExpense?.note ?: "")
    }

    var selectedDate by remember {
        mutableStateOf(initialExpense?.date ?: defaultDate)
    }

    var selectedTime by remember {
        mutableStateOf(initialExpense?.time ?: Formatters.currentTimeString())
    }

    var amountError by remember { mutableStateOf(false) }

    // Quick add amounts in PKR
    val quickAmounts = listOf(10, 50, 100, 200, 500, 1000)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(28.dp))
                .testTag("add_edit_expense_dialog"),
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
                    Text(
                        text = if (isEdit) "Edit Expense" else "Add Expense",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close dialog",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Amount Field
                Text(
                    text = "Amount (PKR) *",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        amountError = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_amount_input"),
                    placeholder = { Text("e.g. 50") },
                    leadingIcon = {
                        Text(
                            text = "Rs.",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = amountError,
                    supportingText = {
                        if (amountError) {
                            Text("Please enter a valid amount", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // Quick Amount Chips
                Text(
                    text = "Quick Select:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickAmounts.forEach { amt ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                                .clickable {
                                    amountText = amt.toString()
                                    amountError = false
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("quick_amount_$amt")
                        ) {
                            Text(
                                text = "Rs. $amt",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Category Selection
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExpenseCategories.all.forEach { cat ->
                        val isSelected = selectedCategory.equals(cat.name, ignoreCase = true)
                        val borderColor = if (isSelected) cat.color else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        val bgColor = if (isSelected) cat.color.copy(alpha = 0.15f) else Color.Transparent

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = borderColor,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .background(bgColor)
                                .clickable { selectedCategory = cat.name }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                                .testTag("category_chip_${cat.name}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = cat.icon,
                                contentDescription = cat.name,
                                tint = if (isSelected) cat.color else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = cat.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                ),
                                color = if (isSelected) cat.color else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Note Field
                Text(
                    text = "Note (Optional)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_note_input"),
                    placeholder = { Text("e.g. Chai with office team, Rickshaw") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Date & Time Pickers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Date Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val cal = Calendar.getInstance()
                                try {
                                    val parts = selectedDate.split("-")
                                    if (parts.size == 3) {
                                        cal.set(Calendar.YEAR, parts[0].toInt())
                                        cal.set(Calendar.MONTH, parts[1].toInt() - 1)
                                        cal.set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                                    }
                                } catch (_: Exception) {}

                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        selectedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                            .testTag("date_picker_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Date",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = Formatters.formatDayMonth(selectedDate),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Time Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val cal = Calendar.getInstance()
                                try {
                                    val sdf = SimpleDateFormat("hh:mm a", Locale.US)
                                    val parsed = sdf.parse(selectedTime)
                                    if (parsed != null) cal.time = parsed
                                } catch (_: Exception) {}

                                TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        val c = Calendar.getInstance()
                                        c.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                        c.set(Calendar.MINUTE, minute)
                                        selectedTime = SimpleDateFormat("hh:mm a", Locale.US).format(c.time)
                                    },
                                    cal.get(Calendar.HOUR_OF_DAY),
                                    cal.get(Calendar.MINUTE),
                                    false
                                ).show()
                            }
                            .testTag("time_picker_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Time",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = selectedTime,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions: Save Button
                Button(
                    onClick = {
                        val amt = amountText.toDoubleOrNull()
                        if (amt == null || amt <= 0.0) {
                            amountError = true
                        } else {
                            onSave(
                                initialExpense?.id ?: 0L,
                                amt,
                                selectedCategory,
                                noteText,
                                selectedDate,
                                selectedTime
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_expense_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (isEdit) "Update Expense" else "Save Expense",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                // If editing, also show Delete option
                if (isEdit && onDelete != null && initialExpense != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onDelete(initialExpense) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("dialog_delete_expense_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Delete Expense",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
