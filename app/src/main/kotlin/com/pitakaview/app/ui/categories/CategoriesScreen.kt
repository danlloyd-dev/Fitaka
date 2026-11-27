package com.pitakaview.app.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pitakaview.app.data.db.FitakaDatabase
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.ui.components.Gloss
import com.pitakaview.app.ui.components.FitakaButton
import com.pitakaview.app.ui.components.FitakaButtonStyle
import com.pitakaview.app.ui.components.FitakaCard
import com.pitakaview.app.ui.components.FitakaDivider
import com.pitakaview.app.ui.components.FitakaListItem
import com.pitakaview.app.ui.components.FitakaScreen
import com.pitakaview.app.ui.components.FitakaTextField
import com.pitakaview.app.ui.components.SectionBadge
import com.pitakaview.app.ui.components.SectionHeader
import com.pitakaview.app.ui.components.sketchBox
import com.pitakaview.app.ui.icons.CategoryIcons
import com.pitakaview.app.ui.icons.FitakaIcons
import com.pitakaview.app.ui.icons.sizeFor
import com.pitakaview.app.ui.theme.FitakaDimens
import com.pitakaview.app.ui.theme.FitakaTheme
import com.pitakaview.app.ui.theme.Section
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The section a category kind belongs to. */
val CategoryKind.section: Section
    get() = if (this == CategoryKind.EXPENSE) Section.GASTOS else Section.KITA

/** That section's name. */
val CategoryKind.sectionLabel: String
    get() = if (this == CategoryKind.EXPENSE) "Gastos" else "Kita"

// ---------- list ----------

class CategoriesViewModel(db: FitakaDatabase) : ViewModel() {
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
    FitakaScreen(title = "Categories", subtitle = "For Gastos and Kita", onBack = onBack) {
        for ((kind, rows) in listOf(CategoryKind.EXPENSE to expense, CategoryKind.INCOME to income)) {
            item(key = "title-$kind") { SectionHeader("${kind.sectionLabel} categories") }
            if (rows.isNotEmpty()) {
                item(key = "list-$kind") { CategoryList(rows, onOpen) }
            }
            item(key = "add-$kind") {
                FitakaButton("Add ${kind.sectionLabel} category", onClick = { onAdd(kind) }, modifier = Modifier.fillMaxWidth(), icon = FitakaIcons.Add)
            }
        }
    }
}

@Composable
private fun CategoryList(rows: List<CategoryEntity>, onOpen: (CategoryEntity) -> Unit) {
    val c = FitakaTheme.colors
    FitakaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        rows.forEachIndexed { i, category ->
            key(category.id) {
                if (i > 0) FitakaDivider()
                FitakaListItem(
                    title = category.name,
                    subtitle = if (category.isArchived) "Hidden" else null,
                    leading = { SectionBadge(category.kind.section, CategoryIcons.forKey(category.iconKey)) },
                    trailing = { Icon(FitakaIcons.ChevronRight, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(20.dp)) },
                    onClick = { onOpen(category) },
                )
            }
        }
    }
}

// ---------- add / edit ----------

class CategoryEditorViewModel(
    private val db: FitakaDatabase,
    categoryId: Long?,
    val kind: CategoryKind,
) : ViewModel() {
    val isEdit = categoryId != null
    val name = TextFieldState()

    var current by mutableStateOf<CategoryEntity?>(null)
        private set

    /** The icon picked for the category. A stored key this version does not know is shown, and saved, as the three dots. */
    var iconKey by mutableStateOf(CategoryIcons.DEFAULT)
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
                current?.let {
                    name.setTextAndPlaceCursorAtEnd(it.name)
                    if (CategoryIcons.has(it.iconKey)) iconKey = it.iconKey
                }
            }
            loaded = true
        }
    }

    fun pickIcon(key: String) {
        if (CategoryIcons.has(key)) iconKey = key
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
                db.categoryDao().insert(CategoryEntity(name = trimmed, kind = kind, iconKey = iconKey, sortOrder = all.size))
            } else {
                db.categoryDao().update(row.copy(name = trimmed, iconKey = iconKey))
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
        Box(Modifier.fillMaxSize().background(FitakaTheme.colors.background))
        return
    }
    CategoryEditorContent(
        vm.isEdit, vm.kind, vm.name, vm.current, vm.nameProblem, vm.iconKey,
        onIcon = vm::pickIcon, onSave = vm::save, onToggleHidden = vm::toggleHidden, onBack = onDone,
    )
}

/** Every icon a category can wear, as one row of choices that wraps. The picked one is filled with ink. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPicker(picked: String, onPick: (String) -> Unit) {
    val c = FitakaTheme.colors
    val gap = Arrangement.spacedBy(8.dp)
    val shape = RoundedCornerShape(12.dp)
    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = gap, verticalArrangement = gap) {
        CategoryIcons.all.forEach { icon ->
            val selected = icon.key == picked
            Box(
                Modifier
                    .size(48.dp)
                    .sketchBox(shape, fill = if (selected) c.ink else c.card, border = c.ink, borderWidth = FitakaDimens.borderThin, gloss = if (selected) Gloss.Badge else null)
                    .clip(shape)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(icon.key) }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon.image, contentDescription = icon.label, tint = if (selected) c.card else c.ink, modifier = Modifier.size(icon.image.sizeFor(26.dp)))
            }
        }
    }
}

@Composable
fun CategoryEditorContent(
    isEdit: Boolean,
    kind: CategoryKind,
    name: TextFieldState,
    current: CategoryEntity?,
    nameProblem: String?,
    iconKey: String,
    onIcon: (String) -> Unit,
    onSave: () -> Unit,
    onToggleHidden: () -> Unit,
    onBack: () -> Unit,
) {
    val c = FitakaTheme.colors
    FitakaScreen(
        title = if (isEdit) "Edit category" else "Add ${kind.sectionLabel} category",
        onBack = onBack,
        pinnedAction = {
            FitakaButton("Save", onClick = onSave, modifier = Modifier.fillMaxWidth(), style = FitakaButtonStyle.Primary, icon = FitakaIcons.Check)
        },
    ) {
        item(key = "name") {
            FitakaTextField(
                state = name, label = "Name", placeholder = if (kind == CategoryKind.EXPENSE) "e.g. Coffee" else "e.g. Bonus",
                isError = nameProblem != null, supportingText = nameProblem,
            )
        }
        item(key = "icon-title") { SectionHeader("Icon") }
        item(key = "icon") { IconPicker(iconKey, onIcon) }
        if (isEdit && current != null) {
            item(key = "hide") {
                Column {
                    FitakaButton(
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
