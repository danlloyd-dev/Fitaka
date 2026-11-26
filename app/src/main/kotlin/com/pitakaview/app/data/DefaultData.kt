package com.pitakaview.app.data

import com.pitakaview.app.data.db.PitakaDatabase
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.WalletType
import java.time.Instant

/** Starter categories and a Cash wallet for a fresh install. Everything here can be renamed or archived. */
object DefaultData {
    /** name to icon key. */
    val expenseCategories = listOf(
        "Food" to "food",
        "Transport" to "transport",
        "Bills" to "bills",
        "Load & Internet" to "phone",
        "Groceries" to "cart",
        "Rent & Home" to "home",
        "Health" to "health",
        "Family" to "family",
        "Fun" to "fun",
        "Other" to "other",
    )

    val incomeCategories = listOf(
        "Salary" to "salary",
        "Sideline" to "sideline",
        "Gift" to "gift",
        "Other" to "other",
    )

    /** Safe to call on every start: each part is only added when its table is empty. */
    suspend fun seedIfEmpty(db: PitakaDatabase, now: Instant) {
        val categories = db.categoryDao()
        if (categories.count() == 0) {
            categories.insertAll(
                expenseCategories.mapIndexed { i, (name, icon) ->
                    CategoryEntity(name = name, kind = CategoryKind.EXPENSE, iconKey = icon, sortOrder = i)
                } + incomeCategories.mapIndexed { i, (name, icon) ->
                    CategoryEntity(name = name, kind = CategoryKind.INCOME, iconKey = icon, sortOrder = i)
                },
            )
        }
        val wallets = db.walletDao()
        if (wallets.count() == 0) {
            wallets.insert(WalletEntity(name = "Cash", type = WalletType.CASH, createdAt = now))
        }
    }
}
