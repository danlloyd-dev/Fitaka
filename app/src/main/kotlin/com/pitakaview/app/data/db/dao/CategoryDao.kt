package com.pitakaview.app.data.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.domain.CategoryKind
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: Long): CategoryEntity?

    /** Every category of a kind, hidden ones last, for the category management screen. */
    @Query("SELECT * FROM categories WHERE kind = :kind ORDER BY isArchived, sortOrder, id")
    fun observeAll(kind: CategoryKind): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    /** How many categories of [kind] are still offered for new entries. */
    @Query("SELECT COUNT(*) FROM categories WHERE kind = :kind AND isArchived = 0")
    suspend fun countActive(kind: CategoryKind): Int

    @Query("SELECT * FROM categories WHERE kind = :kind AND isArchived = 0 ORDER BY sortOrder, id")
    fun observeActive(kind: CategoryKind): Flow<List<CategoryEntity>>
}
