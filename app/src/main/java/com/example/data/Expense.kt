package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.concurrent.atomic.AtomicLong

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val category: String,
    val note: String = "",
    val date: String, // Format: YYYY-MM-DD (e.g. "2026-10-05")
    val time: String, // Format: e.g. "03:45 PM"
    val createdAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val syncStatus: String = "PENDING", // PENDING, SYNCED, FAILED
    val lastSyncedAt: Long? = null,
    val transactionType: String = "EXPENSE", // "EXPENSE" or "CREDIT"
    val transactionId: String = "" // Simple numeric Transaction ID (e.g. "1", "2", "3")
)

private val nextNumericId = AtomicLong(1L)

/**
 * Generates a simple sequential numeric transaction ID ("1", "2", "3", ...).
 */
fun generateTransactionId(): String {
    return nextNumericId.getAndIncrement().toString()
}

/**
 * Sets the counter to start after the given maximum ID.
 */
fun setStartingTransactionId(maxId: Long) {
    nextNumericId.set(maxId + 1)
}

