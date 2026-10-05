package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.ExpenseViewModel
import com.example.ui.components.ExpenseItemCard
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier
) {
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val selectedDateExpenses by viewModel.selectedDateExpenses.collectAsStateWithLifecycle()
    val selectedDateTotal by viewModel.selectedDateTotal.collectAsStateWithLifecycle()
    val datesWithExpenses by viewModel.datesWithExpenses.collectAsStateWithLifecycle()

    val calendarDays = remember(selectedMonth) {
        Formatters.generateCalendarDays(selectedMonth)
    }

    val weekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = "Calendar Expenses",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            actions = {
                // "Today" shortcut button
                OutlinedButton(
                    onClick = {
                        val today = Formatters.todayDateString()
                        viewModel.selectDate(today)
                    },
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .height(34.dp)
                        .testTag("calendar_today_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Today", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("calendar_scroll_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Calendar Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calendar_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Month & Year Navigation Header (< October 2026 >)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.previousMonth() },
                                modifier = Modifier.testTag("calendar_prev_month")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous Month",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            Text(
                                text = Formatters.formatMonthYear(selectedMonth),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("calendar_month_title")
                            )

                            IconButton(
                                onClick = { viewModel.nextMonth() },
                                modifier = Modifier.testTag("calendar_next_month")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next Month",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Weekday Headers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            weekdays.forEach { dayName ->
                                Text(
                                    text = dayName,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    color = if (dayName == "Sun") MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Calendar Days Grid (Rendered in rows of 7 days)
                        val weeks = calendarDays.chunked(7)
                        weeks.forEach { week ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                week.forEach { day ->
                                    val isSelected = day.dateString == selectedDate
                                    val hasExpense = datesWithExpenses.contains(day.dateString)

                                    val textColor = when {
                                        isSelected -> Color.White
                                        day.isToday -> MaterialTheme.colorScheme.primary
                                        !day.isCurrentMonth -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }

                                    val bgModifier = when {
                                        isSelected -> Modifier.background(
                                            MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        day.isToday -> Modifier.border(
                                            1.5.dp,
                                            MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        else -> Modifier
                                    }

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .padding(2.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .then(bgModifier)
                                            .clickable {
                                                viewModel.selectDate(day.dateString)
                                            }
                                            .testTag("calendar_day_${day.dateString}"),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = day.dayNumber.toString(),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isSelected || day.isToday) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 13.sp
                                            ),
                                            color = textColor
                                        )

                                        // Expense indicator dot
                                        if (hasExpense) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(top = 2.dp)
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) Color(0xFFFDE68A) // Gold dot on green
                                                        else MaterialTheme.colorScheme.primary
                                                    )
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.height(7.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Selected Date Summary Header Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("selected_date_summary_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = Formatters.formatDateFull(selectedDate),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Total: ${Formatters.formatPkr(selectedDateTotal)}",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("selected_date_total_text")
                            )
                        }

                        // Button to add expense specifically for this selected date
                        Button(
                            onClick = { viewModel.openAddExpense(selectedDate) },
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("calendar_add_for_date_btn"),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Add", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Section: All Expenses for Selected Date
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Expenses on this day",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${selectedDateExpenses.size} items",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // List of Expenses for selected date
            if (selectedDateExpenses.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .testTag("calendar_empty_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EventNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No expenses on this date",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap '+ Add' to record spending for this day.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(
                    items = selectedDateExpenses,
                    key = { it.id }
                ) { expense ->
                    ExpenseItemCard(
                        expense = expense,
                        onClick = { viewModel.openEditExpense(expense) },
                        onDeleteClick = { viewModel.promptDeleteExpense(expense) }
                    )
                }
            }
        }
    }
}
