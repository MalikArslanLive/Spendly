package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    // Active Expenses
    @Query("SELECT * FROM expenses WHERE isDeleted = 0 ORDER BY date DESC, createdAt DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE isDeleted = 0 AND date = :date ORDER BY createdAt DESC")
    fun getExpensesByDate(date: String): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE isDeleted = 0 AND date LIKE :monthPrefix || '%' ORDER BY date DESC, createdAt DESC")
    fun getExpensesByMonth(monthPrefix: String): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE isDeleted = 0 AND date >= :startDate AND date <= :endDate ORDER BY date DESC, createdAt DESC")
    fun getExpensesByDateRange(startDate: String, endDate: String): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE isDeleted = 0 ORDER BY date ASC, time ASC")
    suspend fun getAllActiveExpensesList(): List<Expense>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: Long): Expense?

    @Query("SELECT * FROM expenses WHERE transactionId = :txnId LIMIT 1")
    suspend fun getExpenseByTransactionId(txnId: String): Expense?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<Expense>)

    @Update
    suspend fun updateExpense(expense: Expense)

    // Trash & Safe Clear operations
    @Query("SELECT * FROM expenses WHERE isDeleted = 1 ORDER BY deletedAt DESC, date DESC")
    fun getTrashExpenses(): Flow<List<Expense>>

    @Query("SELECT COUNT(*) FROM expenses WHERE isDeleted = 1")
    fun getTrashCount(): Flow<Int>

    @Query("UPDATE expenses SET isDeleted = 1, deletedAt = :timestamp, syncStatus = 'PENDING' WHERE id = :id")
    suspend fun moveToTrash(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE expenses SET isDeleted = 1, deletedAt = :timestamp, syncStatus = 'PENDING' WHERE isDeleted = 0")
    suspend fun moveAllToTrash(timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE expenses SET isDeleted = 0, deletedAt = NULL, syncStatus = 'PENDING' WHERE id = :id")
    suspend fun restoreFromTrash(id: Long)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deletePermanently(id: Long)

    @Query("DELETE FROM expenses WHERE isDeleted = 1")
    suspend fun emptyTrash()

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()

    @Query("SELECT COUNT(*) FROM expenses WHERE isDeleted = 0")
    suspend fun getActiveExpenseCount(): Int

    // Sync helpers
    @Query("SELECT * FROM expenses WHERE syncStatus = 'PENDING'")
    suspend fun getPendingSyncExpenses(): List<Expense>

    @Query("UPDATE expenses SET syncStatus = :status, lastSyncedAt = :timestamp WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: String, timestamp: Long)

    @Query("UPDATE expenses SET syncStatus = :status, lastSyncedAt = :timestamp WHERE id IN (:ids)")
    suspend fun markExpensesSynced(ids: List<Long>, status: String = "SYNCED", timestamp: Long = System.currentTimeMillis())
}
