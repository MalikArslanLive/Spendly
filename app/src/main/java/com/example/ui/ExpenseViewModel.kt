package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Expense
import com.example.data.ExpenseRepository
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

class ExpenseViewModel(private val repository: ExpenseRepository) : ViewModel() {

    val allExpenses: StateFlow<List<Expense>> = repository.allExpenses
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedDate = MutableStateFlow(Formatters.todayDateString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _selectedMonth = MutableStateFlow(Formatters.currentMonthString())
    val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()

    // Dialog state for adding/editing expense
    private val _editingExpense = MutableStateFlow<Expense?>(null)
    val editingExpense: StateFlow<Expense?> = _editingExpense.asStateFlow()

    private val _isAddEditOpen = MutableStateFlow(false)
    val isAddEditOpen: StateFlow<Boolean> = _isAddEditOpen.asStateFlow()

    // Confirm delete dialog state
    private val _expenseToDelete = MutableStateFlow<Expense?>(null)
    val expenseToDelete: StateFlow<Expense?> = _expenseToDelete.asStateFlow()

    init {
        // Automatically check if database is empty on initial launch and seed demo expenses
        viewModelScope.launch {
            if (repository.getCount() == 0) {
                seedDemoExpenses()
            }
        }
    }

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

    // Derived: Set of dates with expenses (for Calendar dots)
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
        // Also update month if date is in different month
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
                time = time
            )
            if (id == 0L) {
                repository.insertExpense(expense)
            } else {
                repository.updateExpense(expense)
            }
            closeAddEditDialog()
        }
    }

    fun promptDeleteExpense(expense: Expense) {
        _expenseToDelete.value = expense
    }

    fun cancelDeleteExpense() {
        _expenseToDelete.value = null
    }

    fun confirmDeleteExpense() {
        val expense = _expenseToDelete.value ?: return
        viewModelScope.launch {
            repository.deleteExpense(expense)
            _expenseToDelete.value = null
            if (_editingExpense.value?.id == expense.id) {
                closeAddEditDialog()
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }

    fun seedDemoExpenses() {
        viewModelScope.launch {
            val today = Formatters.todayDateString()
            val sample = listOf(
                Expense(
                    amount = 50.0,
                    category = "Tea/Coffee",
                    note = "Chai",
                    date = today,
                    time = "10:15 AM",
                    createdAt = System.currentTimeMillis() - 1000 * 60 * 180
                ),
                Expense(
                    amount = 100.0,
                    category = "Travel",
                    note = "Rickshaw",
                    date = today,
                    time = "01:30 PM",
                    createdAt = System.currentTimeMillis() - 1000 * 60 * 60
                ),
                Expense(
                    amount = 80.0,
                    category = "Food",
                    note = "Snacks",
                    date = today,
                    time = "04:45 PM",
                    createdAt = System.currentTimeMillis()
                )
            )
            repository.insertAll(sample)
        }
    }
}

class ExpenseViewModelFactory(private val repository: ExpenseRepository) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExpenseViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ExpenseViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
