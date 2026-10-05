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
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val selectedDateCredits by viewModel.selectedDateCredits.collectAsStateWithLifecycle()
    val selectedDateBalance by viewModel.selectedDateBalance.collectAsStateWithLifecycle()
    val datesWithExpenses by viewModel.datesWithExpenses.collectAsStateWithLifecycle()

    val isRangeMode by viewModel.isRangeMode.collectAsStateWithLifecycle()
    val rangeStartDate by viewModel.rangeStartDate.collectAsStateWithLifecycle()
    val rangeEndDate by viewModel.rangeEndDate.collectAsStateWithLifecycle()
    val rangeExpenses by viewModel.rangeExpenses.collectAsStateWithLifecycle()
    val rangeExpensesTotal by viewModel.rangeExpensesTotal.collectAsStateWithLifecycle()
    val rangeCreditsTotal by viewModel.rangeCreditsTotal.collectAsStateWithLifecycle()
    val rangeBalance by viewModel.rangeBalance.collectAsStateWithLifecycle()

    // Internal state for step-by-step range picking: 0 -> picking start, 1 -> picking end
    var rangePickStep by remember { mutableStateOf(0) }

    val calendarDays = remember(selectedMonth) {
        Formatters.generateCalendarDays(selectedMonth)
    }

    val weekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    val rangeStart = minOf(rangeStartDate, rangeEndDate)
    val rangeEnd = maxOf(rangeStartDate, rangeEndDate)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = "Calendar & Reports",
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
                        viewModel.toggleRangeMode(false)
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
            // Mode Selector: Single Date vs Date Range
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isRangeMode,
                        onClick = { viewModel.toggleRangeMode(false) },
                        label = { Text("Single Date", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("mode_single_date_chip")
                    )

                    FilterChip(
                        selected = isRangeMode,
                        onClick = {
                            viewModel.toggleRangeMode(true)
                            rangePickStep = 0
                        },
                        label = { Text("Date Range", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("mode_date_range_chip")
                    )
                }
            }

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

                        if (isRangeMode) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (rangePickStep == 0) "👉 Tap calendar to pick START date" else "👉 Tap calendar to pick END date",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Weekdays Header Row (Sun, Mon, Tue...)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            weekdays.forEach { dayName ->
                                Text(
                                    text = dayName,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (dayName == "Sun" || dayName == "Sat") MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Calendar Days Grid (7 columns per row)
                        val weeks = calendarDays.chunked(7)
                        weeks.forEach { week ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                week.forEach { day ->
                                    val isSelectedSingle = !isRangeMode && day.dateString == selectedDate
                                    val isInRange = isRangeMode && (day.dateString in rangeStart..rangeEnd)
                                    val isRangeBoundary = isRangeMode && (day.dateString == rangeStart || day.dateString == rangeEnd)
                                    val hasExpense = datesWithExpenses.contains(day.dateString)

                                    val textColor = when {
                                        isSelectedSingle || isRangeBoundary -> Color.White
                                        isInRange -> MaterialTheme.colorScheme.onPrimaryContainer
                                        day.isToday -> MaterialTheme.colorScheme.primary
                                        !day.isCurrentMonth -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }

                                    val bgModifier = when {
                                        isSelectedSingle || isRangeBoundary -> Modifier.background(
                                            MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        isInRange -> Modifier.background(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(8.dp)
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
                                                if (isRangeMode) {
                                                    if (rangePickStep == 0) {
                                                        viewModel.setDateRange(day.dateString, day.dateString)
                                                        rangePickStep = 1
                                                    } else {
                                                        viewModel.setDateRange(rangeStartDate, day.dateString)
                                                        rangePickStep = 0
                                                    }
                                                } else {
                                                    viewModel.selectDate(day.dateString)
                                                }
                                            }
                                            .testTag("calendar_day_${day.dateString}"),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = day.dayNumber.toString(),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isSelectedSingle || isRangeBoundary || day.isToday) FontWeight.Bold else FontWeight.Medium,
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
                                                        if (isSelectedSingle || isRangeBoundary) Color(0xFFFDE68A)
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

            // Summary Card (Single Date vs Date Range)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("selected_date_summary_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        if (isRangeMode) {
                            // Date Range Summary: e.g. October 1 – 5, 2026
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${Formatters.formatDisplayDate(rangeStart)} – ${Formatters.formatDisplayDate(rangeEnd)}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Net Balance: ${Formatters.formatPkr(rangeBalance)}",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 22.sp),
                                        color = if (rangeBalance >= 0) Color(0xFF047857) else MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(text = "Credits", style = MaterialTheme.typography.labelSmall, color = Color(0xFF059669))
                                        Text(
                                            text = Formatters.formatPkr(rangeCreditsTotal),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF059669)
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(text = "Expenses", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                        Text(
                                            text = Formatters.formatPkr(rangeExpensesTotal),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        } else {
                            // Single Date Summary
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                        text = "Balance: ${Formatters.formatPkr(selectedDateBalance)}",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 20.sp
                                        ),
                                        color = if (selectedDateBalance >= 0) Color(0xFF047857) else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.testTag("selected_date_total_text")
                                    )
                                    Text(
                                        text = "Credits: ${Formatters.formatPkr(selectedDateCredits)} • Expenses: ${Formatters.formatPkr(selectedDateTotal)}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Button to add transaction specifically for this date
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
                }
            }

            // Transaction List Section
            if (isRangeMode) {
                // Group transactions by day in the range
                val groupedByDay = rangeExpenses.groupBy { it.date }.toSortedMap(compareByDescending { it })

                if (groupedByDay.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("calendar_empty_card"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.EventNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No transactions in this date range",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    groupedByDay.forEach { (dateStr, items) ->
                        item(key = "header_$dateStr") {
                            val dayCredits = items.filter { it.transactionType == "CREDIT" }.sumOf { it.amount }
                            val dayExpenses = items.filter { it.transactionType != "CREDIT" }.sumOf { it.amount }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = Formatters.formatDisplayDate(dateStr),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (dayCredits > 0) "+${Formatters.formatPkr(dayCredits)} / -${Formatters.formatPkr(dayExpenses)}" else Formatters.formatPkr(dayExpenses),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        items(items, key = { it.id }) { expense ->
                            ExpenseItemCard(
                                expense = expense,
                                onClick = { viewModel.openEditExpense(expense) },
                                onDeleteClick = { viewModel.promptDeleteExpense(expense) }
                            )
                        }
                    }
                }
            } else {
                // Single Date Transaction List
                if (selectedDateExpenses.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("calendar_empty_card"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.EventNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No transactions for this date",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = { viewModel.openAddExpense(selectedDate) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Add Transaction")
                                }
                            }
                        }
                    }
                } else {
                    items(selectedDateExpenses, key = { it.id }) { expense ->
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
}
