package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.security.SecureRandom

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
    val transactionId: String = "" // Unique Transaction ID (e.g. "TXN-8F31A92C")
)

/**
 * Generates a unique transaction ID in the format TXN-XXXXXXXX (8 hex chars, e.g. TXN-8F31A92C).
 */
fun generateTransactionId(): String {
    val randomBytes = ByteArray(4)
    SecureRandom().nextBytes(randomBytes)
    val hex = randomBytes.joinToString("") { "%02X".format(it) }
    return "TXN-$hex"
}

