package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Expense
import com.example.data.ExpenseRepository
import com.example.sync.GoogleSheetsSyncManager
import com.example.sync.SyncStatus
import com.example.util.CsvExporter
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
    val count: Int
)

data class CategoryExpenseSummary(
    val category: String,
    val totalAmount: Double,
    val percentage: Float
)

data class MonthlyReportData(
    val monthString: String,
    val totalAmount: Double,
    val totalCount: Int,
    val dailyAverage: Double,
    val categorySummaries: List<CategoryExpenseSummary>,
    val daySummaries: List<DayExpenseSummary>
)

class ExpenseViewModel(
    private val repository: ExpenseRepository,
    val syncManager: GoogleSheetsSyncManager
) : ViewModel() {

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

    // Dialog state for adding/editing expense
    private val _editingExpense = MutableStateFlow<Expense?>(null)
    val editingExpense: StateFlow<Expense?> = _editingExpense.asStateFlow()

    private val _isAddEditOpen = MutableStateFlow(false)
    val isAddEditOpen: StateFlow<Boolean> = _isAddEditOpen.asStateFlow()

    // Confirm delete dialog state (moves to trash)
    private val _expenseToDelete = MutableStateFlow<Expense?>(null)
    val expenseToDelete: StateFlow<Expense?> = _expenseToDelete.asStateFlow()

    // Confirm Clear Data dialog state (moves all to trash)
    private val _isClearDataConfirmOpen = MutableStateFlow(false)
    val isClearDataConfirmOpen: StateFlow<Boolean> = _isClearDataConfirmOpen.asStateFlow()

    // Sync settings dialog state
    private val _isSyncSettingsOpen = MutableStateFlow(false)
    val isSyncSettingsOpen: StateFlow<Boolean> = _isSyncSettingsOpen.asStateFlow()

    // Derived: Today's expenses
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
            expenses.sumOf { it.amount }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    // Derived: Current month total
    val thisMonthTotal: StateFlow<Double> = allExpenses
        .combine(MutableStateFlow(Formatters.currentMonthString())) { expenses, curMonth ->
            expenses.filter { it.date.startsWith(curMonth) }.sumOf { it.amount }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    // Derived: Selected Date expenses & total
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
            expenses.sumOf { it.amount }
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
            val monthExpenses = expenses.filter { it.date.startsWith(month) }
            val total = monthExpenses.sumOf { it.amount }
            val count = monthExpenses.size

            // Category summaries
            val categoryMap = mutableMapOf<String, Double>()
            for (expense in monthExpenses) {
                categoryMap[expense.category] = (categoryMap[expense.category] ?: 0.0) + expense.amount
            }
            val catSummaries = categoryMap.map { (cat, catTotal) ->
                val pct = if (total > 0) (catTotal / total).toFloat() else 0f
                CategoryExpenseSummary(category = cat, totalAmount = catTotal, percentage = pct)
            }.sortedByDescending { it.totalAmount }

            // Day summaries (group by date)
            val dayGroups = monthExpenses.groupBy { it.date }
            val daySummaries = dayGroups.map { (dateStr, list) ->
                DayExpenseSummary(
                    dateString = dateStr,
                    totalAmount = list.sumOf { it.amount },
                    count = list.size
                )
            }.sortedByDescending { it.dateString }

            val activeDaysCount = daySummaries.size
            val dailyAvg = if (activeDaysCount > 0) total / activeDaysCount else 0.0

            MonthlyReportData(
                monthString = month,
                totalAmount = total,
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
        time: String
    ) {
        viewModelScope.launch {
            val expense = Expense(
                id = id,
                amount = amount,
                category = category,
                note = note.trim(),
                date = date,
                time = time,
                isDeleted = false,
                deletedAt = null,
                syncStatus = "PENDING"
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

    fun promptDeleteExpense(expense: Expense) {
        _expenseToDelete.value = expense
    }

    fun cancelDeleteExpense() {
        _expenseToDelete.value = null
    }

    // Moves single expense to trash
    fun confirmDeleteExpense() {
        val expense = _expenseToDelete.value ?: return
        viewModelScope.launch {
            repository.moveToTrash(expense.id)
            _expenseToDelete.value = null
            if (_editingExpense.value?.id == expense.id) {
                closeAddEditDialog()
            }
            syncManager.checkPendingCount()
            syncManager.syncNow()
        }
    }

    // Safe Clear Data
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

    // Trash operations
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

    // CSV Export
    fun exportCsv(context: Context): Boolean {
        return CsvExporter.exportAndShare(context, allExpenses.value)
    }

    // Sync Actions
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
