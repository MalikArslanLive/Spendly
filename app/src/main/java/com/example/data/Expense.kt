package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val lastSyncedAt: Long? = null
)
