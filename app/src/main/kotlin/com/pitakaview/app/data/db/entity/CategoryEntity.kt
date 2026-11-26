package com.pitakaview.app.data.db.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.pitakaview.app.domain.CategoryKind

/** A Kita (income) or Gastos (spending) category, e.g. Salary, Food, Transport. */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["kind", "name"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: CategoryKind,
    /** Key of a drawn icon in the app, e.g. "food". */
    val iconKey: String,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
)
