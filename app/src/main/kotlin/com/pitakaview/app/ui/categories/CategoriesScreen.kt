package com.pitakaview.app.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pitakaview.app.data.db.PitakaDatabase
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.ui.components.PitakaButton
import com.pitakaview.app.ui.components.PitakaButtonStyle
import com.pitakaview.app.ui.components.PitakaCard
import com.pitakaview.app.ui.components.PitakaDivider
import com.pitakaview.app.ui.components.PitakaListItem
import com.pitakaview.app.ui.components.PitakaScreen
import com.pitakaview.app.ui.components.PitakaTextField
import com.pitakaview.app.ui.components.SectionHeader
import com.pitakaview.app.ui.icons.PitakaIcons
import com.pitakaview.app.ui.theme.PitakaTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The section name a category kind belongs to. */
val CategoryKind.sectionLabel: String
    get() = if (this == CategoryKind.EXPENSE) "Gastos" else "Kita"

// ---------- list ----------

class CategoriesViewModel(db: PitakaDatabase) : ViewModel() {
    val expense: StateFlow<List<CategoryEntity>> = db.categoryDao().observeAll(CategoryKind.EXPENSE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val income: StateFlow<List<CategoryEntity>> = db.categoryDao().observeAll(CategoryKind.INCOME)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun CategoriesRoute(onBack: () -> Unit, onAdd: (CategoryKind) -> Unit, onOpen: (CategoryEntity) -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel { CategoriesViewModel(container.database) }
    val expense by vm.expense.collectAsStateWithLifecycle()
    val income by vm.income.collectAsStateWithLifecycle()
    CategoriesContent(expense, income, onBack, onAdd, onOpen)
}

@Composable
fun CategoriesContent(
    expense: List<CategoryEntity>,
    income: List<CategoryEntity>,
    onBack: () -> Unit,
    onAdd: (CategoryKind) -> Unit,
    onOpen: (CategoryEntity) -> Unit,
) {
    PitakaScreen(title = "Categories", subtitle = "For Gastos and Kita", onBack = onBack) {
        for ((kind, rows) in listOf(CategoryKind.EXPENSE to expense, CategoryKind.INCOME to income)) {
            item(key = "title-$kind") { SectionHeader("${kind.sectionLabel} categories") }
            if (rows.isNotEmpty()) {
                item(key = "list-$kind") { CategoryList(rows, onOpen) }
            }
            item(key = "add-$kind") {
                PitakaButton("Add ${kind.sectionLabel} category", onClick = { onAdd(kind) }, modifier = Modifier.fillMaxWidth(), icon = PitakaIcons.Add)
            }
        }
    }
}

@Composable
private fun CategoryList(rows: List<CategoryEntity>, onOpen: (CategoryEntity) -> Unit) {
    val c = PitakaTheme.colors
    PitakaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        rows.forEachIndexed { i, category ->
            key(category.id) {
                if (i > 0) PitakaDivider()
                PitakaListItem(
                    title = category.name,
                    subtitle = if (category.isArchived) "Hidden" else null,
                    trailing = { Icon(PitakaIcons.ChevronRight, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(20.dp)) },
                    onClick = { onOpen(category) },
                )
            }
        }
    }
}

// ---------- add / edit ----------

class CategoryEditorViewModel(
    private val db: PitakaDatabase,
    categoryId: Long?,
    val kind: CategoryKind,
) : ViewModel() {
    val isEdit = categoryId != null
    val name = TextFieldState()

    var current by mutableStateOf<CategoryEntity?>(null)
        private set
    var nameProblem by mutableStateOf<String?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set
    var done by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            if (categoryId != null) {
                current = db.categoryDao().get(categoryId)
                current?.let { name.setTextAndPlaceCursorAtEnd(it.name) }
            }
            loaded = true
        }
    }

    fun save() {
        val trimmed = name.text.toString().trim()
        if (trimmed.isEmpty()) {
            nameProblem = "Give the category a name"
            return
        }
        viewModelScope.launch {
            val all = db.categoryDao().observeAll(kind).first()
            // The database also refuses a duplicate (unique kind + name); checking first lets us say why.
            if (all.any { it.name.equals(trimmed, ignoreCase = true) && it.id != current?.id }) {
                nameProblem = "You already have this category"
                return@launch
            }
            val row = current
            if (row == null) {
                db.categoryDao().insert(CategoryEntity(name = trimmed, kind = kind, iconKey = "other", sortOrder = all.size))
            } else {
                db.categoryDao().update(row.copy(name = trimmed))
            }
            done = true
        }
    }

    fun toggleHidden() {
        val row = current ?: return
        viewModelScope.launch {
            db.categoryDao().update(row.copy(isArchived = !row.isArchived))
            done = true
        }
    }
}

@Composable
fun CategoryEditorRoute(categoryId: Long?, kind: CategoryKind, onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel { CategoryEditorViewModel(container.database, categoryId, kind) }
    LaunchedEffect(vm.done) { if (vm.done) onDone() }
    if (!vm.loaded) {
        Box(Modifier.fillMaxSize().background(PitakaTheme.colors.background))
        return
    }
    CategoryEditorContent(vm.isEdit, vm.kind, vm.name, vm.current, vm.nameProblem, onSave = vm::save, onToggleHidden = vm::toggleHidden, onBack = onDone)
}

@Composable
fun CategoryEditorContent(
    isEdit: Boolean,
    kind: CategoryKind,
    name: TextFieldState,
    current: CategoryEntity?,
    nameProblem: String?,
    onSave: () -> Unit,
    onToggleHidden: () -> Unit,
    onBack: () -> Unit,
) {
    val c = PitakaTheme.colors
    PitakaScreen(
        title = if (isEdit) "Edit category" else "Add ${kind.sectionLabel} category",
        onBack = onBack,
        pinnedAction = {
            PitakaButton("Save", onClick = onSave, modifier = Modifier.fillMaxWidth(), style = PitakaButtonStyle.Primary, icon = PitakaIcons.Check)
        },
    ) {
        item(key = "name") {
            PitakaTextField(
                state = name, label = "Name", placeholder = if (kind == CategoryKind.EXPENSE) "e.g. Coffee" else "e.g. Bonus",
                isError = nameProblem != null, supportingText = nameProblem,
            )
        }
        if (isEdit && current != null) {
            item(key = "hide") {
                Column {
                    PitakaButton(
                        if (current.isArchived) "Show category again" else "Hide category",
                        onClick = onToggleHidden,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "A hidden category keeps its past entries but is no longer offered for new ones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.inkMuted,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }
            }
        }
    }
}
