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

    @Query("SELECT * FROM expenses ORDER BY date DESC, createdAt DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE date = :date ORDER BY createdAt DESC")
    fun getExpensesByDate(date: String): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE date LIKE :monthPrefix || '%' ORDER BY date DESC, createdAt DESC")
    fun getExpensesByMonth(monthPrefix: String): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: Long): Expense?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<Expense>)

    @Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: Long)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()

    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun getExpenseCount(): Int
}
