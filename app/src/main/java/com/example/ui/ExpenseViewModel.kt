package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Expense
import com.example.data.ExpenseRepository
import com.example.data.generateTransactionId
import com.example.sync.GoogleSheetsSyncManager
import com.example.sync.SyncStatus
import com.example.util.Formatters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DayExpenseSummary(
    val dateString: String,
    val totalAmount: Double,
    val totalCredits: Double = 0.0,
    val count: Int
)

data class CategoryExpenseSummary(
    val category: String,
    val totalAmount: Double,
    val percentage: Float
)

data class MonthlyReportData(
    val monthString: String,
    val totalAmount: Double, // Total Expenses
    val totalCredits: Double = 0.0, // Total Credits
    val balance: Double = 0.0, // Credits - Expenses
    val totalCount: Int,
    val dailyAverage: Double,
    val categorySummaries: List<CategoryExpenseSummary>,
    val daySummaries: List<DayExpenseSummary>
)

class ExpenseViewModel(
    private val repository: ExpenseRepository,
    val syncManager: GoogleSheetsSyncManager
) : ViewModel() {

    init {
        viewModelScope.launch {
            repository.migrateLegacyTransactionIds()
            syncManager.checkPendingCount()
            if (syncManager.isSpreadsheetConnected() && syncManager.isOnline()) {
                syncManager.syncNow()
            }
        }
    }

    // Active expenses (excludes trashed/deleted)
    val allExpenses: StateFlow<List<Expense>> = repository.allExpenses
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Trashed expenses
    val trashExpenses: StateFlow<List<Expense>> = repository.trashExpenses
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val trashCount: StateFlow<Int> = repository.trashCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val syncStatus: StateFlow<SyncStatus> = syncManager.syncStatus

    private val _selectedDate = MutableStateFlow(Formatters.todayDateString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _selectedMonth = MutableStateFlow(Formatters.currentMonthString())
    val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()

    // Date Range Selection State
    private val _isRangeMode = MutableStateFlow(false)
    val isRangeMode: StateFlow<Boolean> = _isRangeMode.asStateFlow()

    private val _rangeStartDate = MutableStateFlow(Formatters.todayDateString())
    val rangeStartDate: StateFlow<String> = _rangeStartDate.asStateFlow()

    private val _rangeEndDate = MutableStateFlow(Formatters.todayDateString())
    val rangeEndDate: StateFlow<String> = _rangeEndDate.asStateFlow()

    // Dialog state for adding/editing expense
    private val _editingExpense = MutableStateFlow<Expense?>(null)
    val editingExpense: StateFlow<Expense?> = _editingExpense.asStateFlow()

    private val _isAddEditOpen = MutableStateFlow(false)
    val isAddEditOpen: StateFlow<Boolean> = _isAddEditOpen.asStateFlow()

    // Confirm delete dialog state
    private val _expenseToDelete = MutableStateFlow<Expense?>(null)
    val expenseToDelete: StateFlow<Expense?> = _expenseToDelete.asStateFlow()

    private val _isDeletingSyncedExpense = MutableStateFlow(false)
    val isDeletingSyncedExpense: StateFlow<Boolean> = _isDeletingSyncedExpense.asStateFlow()

    private val _deleteErrorExpense = MutableStateFlow<Expense?>(null)
    val deleteErrorExpense: StateFlow<Expense?> = _deleteErrorExpense.asStateFlow()

    // Confirm Clear Data dialog state (moves all to trash)
    private val _isClearDataConfirmOpen = MutableStateFlow(false)
    val isClearDataConfirmOpen: StateFlow<Boolean> = _isClearDataConfirmOpen.asStateFlow()

    // Sync settings dialog state
    private val _isSyncSettingsOpen = MutableStateFlow(false)
    val isSyncSettingsOpen: StateFlow<Boolean> = _isSyncSettingsOpen.asStateFlow()

    // Derived: Today's transactions
    val todayExpenses: StateFlow<List<Expense>> = allExpenses
        .combine(MutableStateFlow(Formatters.todayDateString())) { expenses, today ->
            expenses.filter { it.date == today }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val todayTotal: StateFlow<Double> = todayExpenses
        .combine(MutableStateFlow(Unit)) { expenses, _ ->
            expenses.filter { it.transactionType != "CREDIT" }.sumOf { it.amount }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    val todayCreditsTotal: StateFlow<Double> = todayExpenses
        .combine(MutableStateFlow(Unit)) { expenses, _ ->
            expenses.filter { it.transactionType == "CREDIT" }.sumOf { it.amount }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    val todayBalance: StateFlow<Double> = combine(todayCreditsTotal, todayTotal) { credits, expenses ->
        credits - expenses
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    // Derived: Current month total
    val thisMonthTotal: StateFlow<Double> = allExpenses
        .combine(MutableStateFlow(Formatters.currentMonthString())) { expenses, curMonth ->
            expenses.filter { it.date.startsWith(curMonth) && it.transactionType != "CREDIT" }.sumOf { it.amount }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    val thisMonthCreditsTotal: StateFlow<Double> = allExpenses
        .combine(MutableStateFlow(Formatters.currentMonthString())) { expenses, curMonth ->
            expenses.filter { it.date.startsWith(curMonth) && it.transactionType == "CREDIT" }.sumOf { it.amount }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    val thisMonthBalance: StateFlow<Double> = combine(thisMonthCreditsTotal, thisMonthTotal) { credits, expenses ->
        credits - expenses
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    // Derived: Selected Date transactions & totals
    val selectedDateExpenses: StateFlow<List<Expense>> = allExpenses
        .combine(selectedDate) { expenses, date ->
            expenses.filter { it.date == date }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val selectedDateTotal: StateFlow<Double> = selectedDateExpenses
        .combine(MutableStateFlow(Unit)) { expenses, _ ->
            expenses.filter { it.transactionType != "CREDIT" }.sumOf { it.amount }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    val selectedDateCredits: StateFlow<Double> = selectedDateExpenses
        .combine(MutableStateFlow(Unit)) { expenses, _ ->
            expenses.filter { it.transactionType == "CREDIT" }.sumOf { it.amount }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    val selectedDateBalance: StateFlow<Double> = combine(selectedDateCredits, selectedDateTotal) { credits, expenses ->
        credits - expenses
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    // Derived: Date Range transactions
    val rangeExpenses: StateFlow<List<Expense>> = combine(allExpenses, rangeStartDate, rangeEndDate) { expenses, start, end ->
        val s = minOf(start, end)
        val e = maxOf(start, end)
        expenses.filter { it.date in s..e }.sortedByDescending { it.date }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val rangeExpensesTotal: StateFlow<Double> = rangeExpenses.combine(MutableStateFlow(Unit)) { list, _ ->
        list.filter { it.transactionType != "CREDIT" }.sumOf { it.amount }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    val rangeCreditsTotal: StateFlow<Double> = rangeExpenses.combine(MutableStateFlow(Unit)) { list, _ ->
        list.filter { it.transactionType == "CREDIT" }.sumOf { it.amount }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    val rangeBalance: StateFlow<Double> = combine(rangeCreditsTotal, rangeExpensesTotal) { credits, expenses ->
        credits - expenses
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    // Derived: Set of dates with active expenses (for Calendar dots)
    val datesWithExpenses: StateFlow<Set<String>> = allExpenses
        .combine(MutableStateFlow(Unit)) { expenses, _ ->
            expenses.map { it.date }.toSet()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    // Derived: Monthly Report Data
    val monthlyReportData: StateFlow<MonthlyReportData> = allExpenses
        .combine(selectedMonth) { expenses, month ->
            val monthTransactions = expenses.filter { it.date.startsWith(month) }
            val expenseTotal = monthTransactions.filter { it.transactionType != "CREDIT" }.sumOf { it.amount }
            val creditTotal = monthTransactions.filter { it.transactionType == "CREDIT" }.sumOf { it.amount }
            val balance = creditTotal - expenseTotal
            val count = monthTransactions.size

            // Category summaries for expenses
            val categoryMap = mutableMapOf<String, Double>()
            for (expense in monthTransactions.filter { it.transactionType != "CREDIT" }) {
                categoryMap[expense.category] = (categoryMap[expense.category] ?: 0.0) + expense.amount
            }
            val catSummaries = categoryMap.map { (cat, catTotal) ->
                val pct = if (expenseTotal > 0) (catTotal / expenseTotal).toFloat() else 0f
                CategoryExpenseSummary(category = cat, totalAmount = catTotal, percentage = pct)
            }.sortedByDescending { it.totalAmount }

            // Day summaries (group by date)
            val dayGroups = monthTransactions.groupBy { it.date }
            val daySummaries = dayGroups.map { (dateStr, list) ->
                DayExpenseSummary(
                    dateString = dateStr,
                    totalAmount = list.filter { it.transactionType != "CREDIT" }.sumOf { it.amount },
                    totalCredits = list.filter { it.transactionType == "CREDIT" }.sumOf { it.amount },
                    count = list.size
                )
            }.sortedByDescending { it.dateString }

            val activeDaysCount = daySummaries.size
            val dailyAvg = if (activeDaysCount > 0) expenseTotal / activeDaysCount else 0.0

            MonthlyReportData(
                monthString = month,
                totalAmount = expenseTotal,
                totalCredits = creditTotal,
                balance = balance,
                totalCount = count,
                dailyAverage = dailyAvg,
                categorySummaries = catSummaries,
                daySummaries = daySummaries
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MonthlyReportData(
                monthString = Formatters.currentMonthString(),
                totalAmount = 0.0,
                totalCredits = 0.0,
                balance = 0.0,
                totalCount = 0,
                dailyAverage = 0.0,
                categorySummaries = emptyList(),
                daySummaries = emptyList()
            )
        )

    // Actions
    fun selectDate(date: String) {
        _selectedDate.value = date
        val dateMonth = if (date.length >= 7) date.substring(0, 7) else _selectedMonth.value
        _selectedMonth.value = dateMonth
    }

    fun selectMonth(month: String) {
        _selectedMonth.value = month
    }

    fun previousMonth() {
        _selectedMonth.value = Formatters.getPreviousMonth(_selectedMonth.value)
    }

    fun nextMonth() {
        _selectedMonth.value = Formatters.getNextMonth(_selectedMonth.value)
    }

    fun setDateRange(startDate: String, endDate: String) {
        val s = minOf(startDate, endDate)
        val e = maxOf(startDate, endDate)
        _rangeStartDate.value = s
        _rangeEndDate.value = e
        _isRangeMode.value = true
    }

    fun toggleRangeMode(enable: Boolean) {
        _isRangeMode.value = enable
        if (enable && _rangeStartDate.value.isBlank()) {
            _rangeStartDate.value = _selectedDate.value
            _rangeEndDate.value = _selectedDate.value
        }
    }

    fun openAddExpense(initialDate: String? = null) {
        _editingExpense.value = null
        if (initialDate != null) {
            _selectedDate.value = initialDate
        }
        _isAddEditOpen.value = true
    }

    fun openEditExpense(expense: Expense) {
        _editingExpense.value = expense
        _isAddEditOpen.value = true
    }

    fun closeAddEditDialog() {
        _isAddEditOpen.value = false
        _editingExpense.value = null
    }

    fun saveExpense(
        id: Long,
        amount: Double,
        category: String,
        note: String,
        date: String,
        time: String,
        transactionType: String = "EXPENSE"
    ) {
        viewModelScope.launch {
            val existingTxnId = _editingExpense.value?.transactionId
            val txnId = if (id != 0L && !existingTxnId.isNullOrBlank() && existingTxnId.toLongOrNull() != null) {
                existingTxnId
            } else {
                repository.getNextTransactionId()
            }

            val expense = Expense(
                id = id,
                amount = amount,
                category = category,
                note = note.trim(),
                date = date,
                time = time,
                isDeleted = false,
                deletedAt = null,
                syncStatus = "PENDING",
                transactionType = transactionType,
                transactionId = txnId
            )
            if (id == 0L) {
                repository.insertExpense(expense)
            } else {
                repository.updateExpense(expense)
            }
            closeAddEditDialog()
            syncManager.checkPendingCount()
            syncManager.syncNow()
        }
    }

    // Overloaded for backward compatibility with 6-arg calls
    fun saveExpense(
        id: Long,
        amount: Double,
        category: String,
        note: String,
        date: String,
        time: String
    ) {
        saveExpense(id, amount, category, note, date, time, "EXPENSE")
    }

    fun isExpenseSyncedToGoogleSheets(expense: Expense): Boolean {
        return expense.syncStatus == "SYNCED" && syncManager.isSpreadsheetConnected()
    }

    fun promptDeleteExpense(expense: Expense) {
        _expenseToDelete.value = expense
    }

    fun cancelDeleteExpense() {
        _expenseToDelete.value = null
        _isDeletingSyncedExpense.value = false
    }

    fun retryDeleteExpense() {
        val expense = _deleteErrorExpense.value ?: return
        _deleteErrorExpense.value = null
        _expenseToDelete.value = expense
        confirmDeleteExpense()
    }

    fun dismissDeleteError() {
        _deleteErrorExpense.value = null
    }

    fun confirmDeleteExpense() {
        val expense = _expenseToDelete.value ?: _deleteErrorExpense.value ?: return
        val isSynced = isExpenseSyncedToGoogleSheets(expense)

        if (!isSynced) {
            // Unsynced transaction: move to trash directly
            viewModelScope.launch {
                repository.moveToTrash(expense.id)
                _expenseToDelete.value = null
                _deleteErrorExpense.value = null
                if (_editingExpense.value?.id == expense.id) {
                    closeAddEditDialog()
                }
                syncManager.checkPendingCount()
                syncManager.syncNow()
            }
            return
        }

        // Synced transaction: delete exact row from Google Sheets first, then move to trash
        viewModelScope.launch {
            _isDeletingSyncedExpense.value = true
            val result = syncManager.deleteTransactionFromSheet(expense)
            _isDeletingSyncedExpense.value = false

            if (result.isSuccess) {
                repository.moveToTrash(expense.id)
                _expenseToDelete.value = null
                _deleteErrorExpense.value = null
                if (_editingExpense.value?.id == expense.id) {
                    closeAddEditDialog()
                }
                syncManager.checkPendingCount()
                syncManager.syncNow()
            } else {
                // If Google Sheets deletion fails, keep transaction intact and allow retry
                _expenseToDelete.value = null
                _deleteErrorExpense.value = expense
            }
        }
    }

    fun promptClearAllData() {
        _isClearDataConfirmOpen.value = true
    }

    fun dismissClearAllData() {
        _isClearDataConfirmOpen.value = false
    }

    fun confirmClearAllDataToTrash() {
        viewModelScope.launch {
            repository.moveAllToTrash()
            _isClearDataConfirmOpen.value = false
            syncManager.checkPendingCount()
            syncManager.syncNow()
        }
    }

    fun restoreExpense(id: Long) {
        viewModelScope.launch {
            repository.restoreFromTrash(id)
            syncManager.checkPendingCount()
            syncManager.syncNow()
        }
    }

    fun deletePermanently(id: Long) {
        viewModelScope.launch {
            repository.deletePermanently(id)
            syncManager.checkPendingCount()
            syncManager.syncNow()
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
            syncManager.checkPendingCount()
            syncManager.syncNow()
        }
    }

    fun openSyncSettings() {
        _isSyncSettingsOpen.value = true
    }

    fun closeSyncSettings() {
        _isSyncSettingsOpen.value = false
    }

    fun syncNow() {
        syncManager.syncNow()
    }
}

class ExpenseViewModelFactory(
    private val repository: ExpenseRepository,
    private val syncManager: GoogleSheetsSyncManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExpenseViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ExpenseViewModel(repository, syncManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
