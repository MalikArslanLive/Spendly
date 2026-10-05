package com.example.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.CatBills
import com.example.ui.theme.CatFamily
import com.example.ui.theme.CatFood
import com.example.ui.theme.CatHealth
import com.example.ui.theme.CatOther
import com.example.ui.theme.CatShopping
import com.example.ui.theme.CatTeaCoffee
import com.example.ui.theme.CatTravel
import com.example.ui.theme.CatWork

data class CategoryInfo(
    val name: String,
    val icon: ImageVector,
    val color: Color
)

object ExpenseCategories {
    val FOOD = CategoryInfo("Food", Icons.Default.Restaurant, CatFood)
    val TEA_COFFEE = CategoryInfo("Tea/Coffee", Icons.Default.Coffee, CatTeaCoffee)
    val TRAVEL = CategoryInfo("Travel", Icons.Default.DirectionsCar, CatTravel)
    val SHOPPING = CategoryInfo("Shopping", Icons.Default.ShoppingBag, CatShopping)
    val BILLS = CategoryInfo("Bills", Icons.Default.ReceiptLong, CatBills)
    val FAMILY = CategoryInfo("Family", Icons.Default.Diversity3, CatFamily)
    val WORK = CategoryInfo("Work", Icons.Default.BusinessCenter, CatWork)
    val HEALTH = CategoryInfo("Health", Icons.Default.LocalHospital, CatHealth)
    val OTHER = CategoryInfo("Other", Icons.Default.MoreHoriz, CatOther)

    val all: List<CategoryInfo> = listOf(
        FOOD,
        TEA_COFFEE,
        TRAVEL,
        SHOPPING,
        BILLS,
        FAMILY,
        WORK,
        HEALTH,
        OTHER
    )

    fun getCategoryInfo(name: String): CategoryInfo {
        return all.find { it.name.equals(name, ignoreCase = true) } ?: OTHER
    }
}
