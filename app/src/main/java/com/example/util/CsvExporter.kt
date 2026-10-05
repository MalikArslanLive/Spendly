package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.Expense
import java.io.File
import java.io.FileWriter

object CsvExporter {

    fun generateCsvString(expenses: List<Expense>): String {
        val sb = StringBuilder()
        sb.append("Date,Time,Amount,Category,Note\n")

        for (expense in expenses) {
            val amountFormatted = if (expense.amount % 1.0 == 0.0) {
                expense.amount.toLong().toString()
            } else {
                expense.amount.toString()
            }

            val escapedCategory = escapeCsvField(expense.category)
            val escapedNote = escapeCsvField(expense.note)

            sb.append("${expense.date},${expense.time},$amountFormatted,$escapedCategory,$escapedNote\n")
        }

        return sb.toString()
    }

    private fun escapeCsvField(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    fun exportAndShare(context: Context, expenses: List<Expense>): Boolean {
        try {
            val csvContent = generateCsvString(expenses)
            val exportFile = File(context.cacheDir, "my_expenses_export.csv")
            FileWriter(exportFile).use { writer ->
                writer.write(csvContent)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                exportFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Spendly Export")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Export Spendly CSV")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
