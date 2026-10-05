package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.ExpenseRepository
import com.example.sync.GoogleSheetsSyncManager
import com.example.ui.ExpenseViewModel
import com.example.ui.ExpenseViewModelFactory
import com.example.ui.components.AddEditExpenseDialog
import com.example.ui.components.ClearDataConfirmDialog
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.GoogleSheetDeleteErrorDialog
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MonthlyScreen
import com.example.ui.screens.SyncSettingsDialog
import com.example.ui.screens.TrashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.Formatters

class MainActivity : ComponentActivity() {

    private val viewModel: ExpenseViewModel by viewModels {
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = ExpenseRepository(database.expenseDao(), applicationContext)
        val syncManager = GoogleSheetsSyncManager(applicationContext, repository, lifecycleScope)
        ExpenseViewModelFactory(repository, syncManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MyExpensesApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MyExpensesApp(viewModel: ExpenseViewModel) {
    var currentScreenIndex by rememberSaveable { mutableIntStateOf(0) }
    var isTrashOpen by rememberSaveable { mutableStateOf(false) }

    val isAddEditOpen by viewModel.isAddEditOpen.collectAsStateWithLifecycle()
    val editingExpense by viewModel.editingExpense.collectAsStateWithLifecycle()
    val expenseToDelete by viewModel.expenseToDelete.collectAsStateWithLifecycle()
    val isDeletingSyncedExpense by viewModel.isDeletingSyncedExpense.collectAsStateWithLifecycle()
    val deleteErrorExpense by viewModel.deleteErrorExpense.collectAsStateWithLifecycle()
    val isClearDataConfirmOpen by viewModel.isClearDataConfirmOpen.collectAsStateWithLifecycle()
    val isSyncSettingsOpen by viewModel.isSyncSettingsOpen.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()

    // BackHandler: if in Trash, back closes Trash; if on secondary tab, back returns to Home
    if (isTrashOpen) {
        BackHandler {
            isTrashOpen = false
        }
    } else if (currentScreenIndex != 0) {
        BackHandler {
            currentScreenIndex = 0
        }
    }

    if (isTrashOpen) {
        TrashScreen(
            viewModel = viewModel,
            onNavigateBack = { isTrashOpen = false }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            floatingActionButton = {
                // Prominent Add Expense FAB
                FloatingActionButton(
                    onClick = {
                        val dateForNewExpense = if (currentScreenIndex == 1) selectedDate else Formatters.todayDateString()
                        viewModel.openAddExpense(dateForNewExpense)
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("main_add_expense_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Expense",
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.testTag("bottom_nav_bar"),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentScreenIndex == 0,
                        onClick = { currentScreenIndex = 0 },
                        icon = {
                            Icon(
                                imageVector = if (currentScreenIndex == 0) Icons.Filled.Home else Icons.Outlined.Home,
                                contentDescription = "Home"
                            )
                        },
                        label = {
                            Text(
                                text = "Home",
                                fontWeight = if (currentScreenIndex == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_home_tab")
                    )

                    NavigationBarItem(
                        selected = currentScreenIndex == 1,
                        onClick = { currentScreenIndex = 1 },
                        icon = {
                            Icon(
                                imageVector = if (currentScreenIndex == 1) Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth,
                                contentDescription = "Calendar"
                            )
                        },
                        label = {
                            Text(
                                text = "Calendar",
                                fontWeight = if (currentScreenIndex == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_calendar_tab")
                    )

                    NavigationBarItem(
                        selected = currentScreenIndex == 2,
                        onClick = { currentScreenIndex = 2 },
                        icon = {
                            Icon(
                                imageVector = if (currentScreenIndex == 2) Icons.Filled.PieChart else Icons.Outlined.PieChart,
                                contentDescription = "Monthly"
                            )
                        },
                        label = {
                            Text(
                                text = "Monthly",
                                fontWeight = if (currentScreenIndex == 2) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_monthly_tab")
                    )
                }
            }
        ) { innerPadding ->
            when (currentScreenIndex) {
                0 -> HomeScreen(
                    viewModel = viewModel,
                    onAddExpenseClick = {
                        viewModel.openAddExpense(Formatters.todayDateString())
                    },
                    onOpenTrash = { isTrashOpen = true },
                    onOpenSyncSettings = { viewModel.openSyncSettings() },
                    modifier = Modifier.padding(innerPadding)
                )
                1 -> CalendarScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
                2 -> MonthlyScreen(
                    viewModel = viewModel,
                    onNavigateToDate = { dateStr ->
                        viewModel.selectDate(dateStr)
                        currentScreenIndex = 1 // Switch to Calendar screen
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            // Add / Edit Expense Dialog
            if (isAddEditOpen) {
                AddEditExpenseDialog(
                    initialExpense = editingExpense,
                    defaultDate = if (currentScreenIndex == 1) selectedDate else Formatters.todayDateString(),
                    onDismiss = { viewModel.closeAddEditDialog() },
                    onSave = { id, amount, category, note, date, time, transactionType ->
                        viewModel.saveExpense(id, amount, category, note, date, time, transactionType)
                    },
                    onDelete = { expense ->
                        viewModel.promptDeleteExpense(expense)
                    }
                )
            }

            // Confirm Delete Dialog
            if (expenseToDelete != null) {
                val exp = expenseToDelete!!
                DeleteConfirmDialog(
                    expense = exp,
                    isSynced = viewModel.isExpenseSyncedToGoogleSheets(exp),
                    isDeleting = isDeletingSyncedExpense,
                    onConfirm = { viewModel.confirmDeleteExpense() },
                    onDismiss = { viewModel.cancelDeleteExpense() }
                )
            }

            // Google Sheets delete error dialog (shown when Google Sheets is unavailable or deletion fails)
            if (deleteErrorExpense != null) {
                GoogleSheetDeleteErrorDialog(
                    onRetry = { viewModel.retryDeleteExpense() },
                    onCancel = { viewModel.dismissDeleteError() }
                )
            }

            // Safe Clear Data Confirmation Dialog (Moves all to Trash)
            if (isClearDataConfirmOpen) {
                ClearDataConfirmDialog(
                    onConfirm = { viewModel.confirmClearAllDataToTrash() },
                    onDismiss = { viewModel.dismissClearAllData() }
                )
            }

            // Google Sheets Sync Settings Dialog
            if (isSyncSettingsOpen) {
                SyncSettingsDialog(
                    viewModel = viewModel,
                    onDismiss = { viewModel.closeSyncSettings() }
                )
            }
        }
    }
}
