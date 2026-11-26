package com.pitakaview.app.ui

import androidx.compose.foundation.text.input.TextFieldState
import com.github.takahirom.roborazzi.captureRoboImage
import com.pitakaview.app.data.DefaultData
import com.pitakaview.app.data.db.dao.RecentTransaction
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.ui.categories.CategoriesContent
import com.pitakaview.app.ui.categories.CategoryEditorContent
import com.pitakaview.app.ui.home.HomeContent
import com.pitakaview.app.ui.home.HomeState
import com.pitakaview.app.ui.theme.PitakaTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** The screens the rough-edge fixes changed, drawn for real so the icons and wording are judged from pictures. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1500dp-hdpi")
class RoughEdgesScreenshotTest {
    private val today = LocalDate.of(2026, 11, 26)

    private val expense = DefaultData.expenseCategories.mapIndexed { i, (name, icon) -> CategoryEntity(id = 1L + i, name = name, kind = CategoryKind.EXPENSE, iconKey = icon, sortOrder = i) }
    private val income = DefaultData.incomeCategories.mapIndexed { i, (name, icon) -> CategoryEntity(id = 50L + i, name = name, kind = CategoryKind.INCOME, iconKey = icon, sortOrder = i) }

    /** Every starter category with the icon it wears. */
    @Test
    fun categoriesWithTheirIcons() = captureRoboImage("build/outputs/roborazzi/screen_categories_icons_mango_light.png") {
        PitakaTheme(ThemeId.MANGO, DarkModePref.LIGHT) { CategoriesContent(expense, income, onBack = {}, onAdd = {}, onOpen = {}) }
    }

    /** The picker with every icon there is, one picked. */
    @Test
    fun theIconPicker() = captureRoboImage("build/outputs/roborazzi/screen_category_editor_icons_ube_dark.png") {
        PitakaTheme(ThemeId.UBE, DarkModePref.DARK) {
            CategoryEditorContent(
                isEdit = true, kind = CategoryKind.EXPENSE, name = TextFieldState("Kape"), current = expense.first().copy(name = "Kape"),
                nameProblem = null, iconKey = "coffee", onIcon = {}, onSave = {}, onToggleHidden = {}, onBack = {},
            )
        }
    }

    /** Home after a restore that broke off, with rows that say their note and wear their category's icon. */
    @Test
    fun homeAfterAFailedRestore() = captureRoboImage("build/outputs/roborazzi/screen_home_restore_failed_mango_light.png") {
        val state = HomeState(
            recent = listOf(
                RecentTransaction(5, TxPurpose.EXPENSE, 12_500, today, "lugaw", "Food", "Cash", null, categoryIconKey = "food"),
                RecentTransaction(4, TxPurpose.EXPENSE, 50_000, today.minusDays(1), "Shopee · Payment", "Other", "MariBank", null, categoryIconKey = "other"),
                RecentTransaction(3, TxPurpose.INCOME, 100_000, today.minusDays(2), "Tita Baby · Transfer", "Other", null, "MariBank", categoryIconKey = "other"),
                RecentTransaction(2, TxPurpose.TRANSFER, 300_000, today.minusDays(3), null, null, "MariBank", "GCash"),
                RecentTransaction(1, TxPurpose.INCOME, 2_000_000, LocalDate.of(2026, 11, 15), null, "Salary", null, "MariBank", categoryIconKey = "salary"),
            ),
        )
        PitakaTheme(ThemeId.MANGO, DarkModePref.LIGHT) {
            HomeContent(state, today, "Magandang hapon!", {}, {}, {}, restoreFailed = true)
        }
    }
}
