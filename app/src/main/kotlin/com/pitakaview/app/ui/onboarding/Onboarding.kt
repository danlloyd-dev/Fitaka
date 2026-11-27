package com.pitakaview.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room3.withWriteTransaction
import com.pitakaview.app.APP_NAME
import com.pitakaview.app.data.db.FitakaDatabase
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.data.prefs.SettingsRepository
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.domain.EntryFormLogic
import com.pitakaview.app.domain.Peso
import com.pitakaview.app.domain.WalletType
import com.pitakaview.app.ui.components.Gloss
import com.pitakaview.app.ui.components.MoneyInput
import com.pitakaview.app.ui.components.FitakaButton
import com.pitakaview.app.ui.components.FitakaButtonStyle
import com.pitakaview.app.ui.components.FitakaCard
import com.pitakaview.app.ui.components.FitakaChip
import com.pitakaview.app.ui.components.FitakaScreen
import com.pitakaview.app.ui.components.FitakaTextField
import com.pitakaview.app.ui.components.SectionHeader
import com.pitakaview.app.ui.components.sketchBox
import com.pitakaview.app.ui.icons.FitakaIcons
import com.pitakaview.app.ui.icons.icon
import com.pitakaview.app.ui.icons.sizeFor
import com.pitakaview.app.ui.mascot.Alkansya
import com.pitakaview.app.ui.mascot.AlkansyaMood
import com.pitakaview.app.ui.theme.FitakaDimens
import com.pitakaview.app.ui.theme.FitakaTheme
import com.pitakaview.app.ui.theme.Section
import com.pitakaview.app.ui.wallets.WalletPreset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

/** The e-wallets and bank the welcome offers by name; anything else is added later in Settings. */
val OnboardingWallets = listOf(WalletPreset.GCASH, WalletPreset.MAYA, WalletPreset.MARIBANK)

/** What was typed on the "Your money now" step. An empty box means ₱0.00. */
data class OnboardingForm(val cashText: String = "", val picked: Map<WalletPreset, String> = emptyMap())

/** What finishing the welcome will store: the cash on hand, and each other wallet with its balance. */
data class OnboardingPlan(val cashCentavos: Long, val wallets: List<Pair<WalletPreset, Long>>)

/** The rules of the welcome, apart from the screens. Pure, so every rule is unit-tested. */
object OnboardingLogic {
    const val PAGES = 3

    /** The typed amount, ₱0.00 for an empty box, or null when it does not read as an amount. */
    fun amount(text: String): Long? = if (text.isBlank()) 0 else Peso.parse(text)

    /** The boxes that do not read as an amount; [WalletPreset.CASH] stands for the cash box. */
    fun problems(form: OnboardingForm): Set<WalletPreset> = buildSet {
        if (amount(form.cashText) == null) add(WalletPreset.CASH)
        form.picked.forEach { (preset, text) -> if (amount(text) == null) add(preset) }
    }

    /** Call only when [problems] is empty. Wallets come out in the order they are offered, not the order they were tapped. */
    fun plan(form: OnboardingForm): OnboardingPlan = OnboardingPlan(
        cashCentavos = requireNotNull(amount(form.cashText)) { "Cash must be checked with problems() first" },
        wallets = OnboardingWallets.filter { it in form.picked }.map { preset ->
            preset to requireNotNull(amount(form.picked.getValue(preset))) { "${preset.label} must be checked with problems() first" }
        },
    )

    /**
     * Whether to show the welcome when the app opens: only until it has been finished or skipped, and
     * never to someone who already has entries. Once the answer is no it is remembered, so later
     * launches read one saved setting and ask the database nothing.
     */
    suspend fun isNeeded(settings: SettingsRepository, db: FitakaDatabase): Boolean {
        if (settings.onboardingDone.first()) return false
        if (db.transactionDao().count() == 0) return true
        settings.setOnboardingDone()
        return false
    }

    /**
     * Stores the plan as one all-or-nothing step. It runs only while nothing is logged, so a wallet's
     * balance is simply its starting balance. A wallet that is already there (an app set up by hand
     * before the welcome existed) is given the amount instead of being added a second time.
     */
    suspend fun apply(db: FitakaDatabase, plan: OnboardingPlan, now: Instant) {
        db.withWriteTransaction {
            val dao = db.walletDao()
            val existing = dao.all()
            var nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
            val cash = existing.firstOrNull { it.type == WalletType.CASH && !it.isArchived }
            if (cash == null) {
                dao.insert(WalletEntity(name = "Cash", type = WalletType.CASH, openingBalanceCentavos = plan.cashCentavos, sortOrder = nextOrder++, createdAt = now))
            } else if (cash.openingBalanceCentavos != plan.cashCentavos) {
                dao.update(cash.copy(openingBalanceCentavos = plan.cashCentavos))
            }
            for ((preset, centavos) in plan.wallets) {
                val same = existing.firstOrNull { it.provider == preset.provider && !it.isArchived }
                if (same == null) {
                    dao.insert(
                        WalletEntity(
                            name = preset.suggestedName, type = preset.type, provider = preset.provider,
                            openingBalanceCentavos = centavos, sortOrder = nextOrder++, createdAt = now,
                        ),
                    )
                } else if (same.openingBalanceCentavos != centavos) {
                    dao.update(same.copy(openingBalanceCentavos = centavos))
                }
            }
        }
    }
}

/** The welcome's state. Kept in a ViewModel so a rotation keeps the step and what was typed. */
class OnboardingViewModel(
    private val db: FitakaDatabase,
    private val settings: SettingsRepository,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {
    var page by mutableIntStateOf(0)
        private set
    val cash = TextFieldState()
    val amounts: Map<WalletPreset, TextFieldState> = OnboardingWallets.associateWith { TextFieldState() }
    var picked by mutableStateOf(emptySet<WalletPreset>())
        private set

    /** Shown only after trying to go on from the money step. */
    var problems by mutableStateOf(emptySet<WalletPreset>())
        private set
    var loaded by mutableStateOf(false)
        private set
    var done by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            // An app that already has wallets (set up by hand) starts from what is there.
            val wallets = db.walletDao().all().filter { !it.isArchived }
            wallets.firstOrNull { it.type == WalletType.CASH }?.takeIf { it.openingBalanceCentavos > 0 }
                ?.let { cash.setTextAndPlaceCursorAtEnd(EntryFormLogic.plainAmount(it.openingBalanceCentavos)) }
            for (preset in OnboardingWallets) {
                val wallet = wallets.firstOrNull { it.provider == preset.provider } ?: continue
                picked = picked + preset
                if (wallet.openingBalanceCentavos > 0) {
                    amounts.getValue(preset).setTextAndPlaceCursorAtEnd(EntryFormLogic.plainAmount(wallet.openingBalanceCentavos))
                }
            }
            loaded = true
        }
    }

    private fun form() = OnboardingForm(cash.text.toString(), picked.associateWith { amounts.getValue(it).text.toString() })

    fun toggle(preset: WalletPreset) {
        picked = if (preset in picked) picked - preset else picked + preset
        problems = problems - preset
    }

    fun next() {
        if (page == 1) {
            problems = OnboardingLogic.problems(form())
            if (problems.isNotEmpty()) return
        }
        if (page < OnboardingLogic.PAGES - 1) page++
    }

    fun back() {
        if (page > 0) page--
    }

    /** The last step's button: store what was typed and never show the welcome again. */
    fun finish() {
        val form = form()
        problems = OnboardingLogic.problems(form)
        if (problems.isNotEmpty()) {
            page = 1
            return
        }
        viewModelScope.launch {
            OnboardingLogic.apply(db, OnboardingLogic.plan(form), now())
            settings.setOnboardingDone()
            done = true
        }
    }

    /** Leaves at once, storing nothing that was typed. */
    fun skip() {
        viewModelScope.launch {
            settings.setOnboardingDone()
            done = true
        }
    }
}

@Composable
fun OnboardingRoute(onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel { OnboardingViewModel(container.database, container.settings) }
    LaunchedEffect(vm.done) { if (vm.done) onDone() }
    if (!vm.loaded) {
        Box(Modifier.fillMaxSize().background(FitakaTheme.colors.background))
        return
    }
    OnboardingContent(
        page = vm.page, cash = vm.cash, amounts = vm.amounts, picked = vm.picked, problems = vm.problems,
        onToggle = vm::toggle, onNext = vm::next, onBack = vm::back, onFinish = vm::finish, onSkip = vm::skip,
    )
}

private val SectionLines = mapOf(
    Section.KITA to "Money coming in: your pay and other income.",
    Section.BAON to "Allowance you get or give, and what is left of it.",
    Section.IPON to "Savings, and what you are saving for.",
    Section.GASTOS to "Everything you spend.",
    Section.UTANG to "Money you owe, and money owed to you.",
)

private val Tips = listOf(
    "Tap Add transaction on Home whenever you spend or receive money.",
    "Set up your paydays in Kita and your loans in Utang to see what is coming.",
    "In Settings you can turn on reminders, lock the app, and back up your data.",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingContent(
    page: Int,
    cash: TextFieldState,
    amounts: Map<WalletPreset, TextFieldState>,
    picked: Set<WalletPreset>,
    problems: Set<WalletPreset>,
    onToggle: (WalletPreset) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
) {
    val c = FitakaTheme.colors
    val type = MaterialTheme.typography
    val money = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    val last = page == OnboardingLogic.PAGES - 1
    FitakaScreen(
        title = when (page) {
            0 -> "Welcome"
            1 -> "Your money now"
            else -> "All set"
        },
        subtitle = "Step ${page + 1} of ${OnboardingLogic.PAGES}",
        onBack = if (page > 0) onBack else null,
        actions = {
            // Not on the last step: there, the main button leaves anyway.
            if (!last) {
                Box(
                    Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onSkip).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Skip", style = type.labelLarge, color = c.ink)
                }
            }
        },
        pinnedAction = {
            FitakaButton(
                text = when (page) {
                    0 -> "Start"
                    1 -> "Next"
                    else -> "Open my wallet"
                },
                onClick = if (last) onFinish else onNext,
                modifier = Modifier.fillMaxWidth(),
                style = FitakaButtonStyle.Primary,
                icon = if (last) FitakaIcons.Check else null,
            )
        },
    ) {
        when (page) {
            0 -> {
                item(key = "hello") {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Alkansya(mood = AlkansyaMood.HELLO, width = 168.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(APP_NAME, style = type.headlineSmall, color = c.ink, textAlign = TextAlign.Center)
                        Text(
                            "It keeps track of your money in five places.",
                            style = type.bodyLarge, color = c.inkMuted, textAlign = TextAlign.Center,
                        )
                    }
                }
                item(key = "sections") {
                    FitakaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                        Section.entries.forEach { section ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(40.dp).sketchBox(RoundedCornerShape(FitakaDimens.cornerSmall), c.fill(section), c.ink, borderWidth = FitakaDimens.borderThin, gloss = Gloss.Badge),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(section.icon, contentDescription = null, tint = c.onSectionFill, modifier = Modifier.size(section.icon.sizeFor(22.dp)))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(section.label, style = type.titleSmall, color = c.ink)
                                    Text(SectionLines.getValue(section), style = type.bodyMedium, color = c.inkMuted)
                                }
                            }
                        }
                    }
                }
                item(key = "private") {
                    Text("Everything stays on this phone.", style = type.bodyMedium, color = c.inkMuted, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            1 -> {
                item(key = "ask") {
                    Text(
                        "How much money do you have right now? A rough number is fine, and you can change it later.",
                        style = type.bodyLarge, color = c.ink, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                item(key = "cash") {
                    FitakaTextField(
                        state = cash, label = "Cash on hand", placeholder = "0.00", prefix = Peso.SIGN,
                        isError = WalletPreset.CASH in problems,
                        supportingText = if (WalletPreset.CASH in problems) "Enter an amount, or leave it empty" else null,
                        inputTransformation = MoneyInput, keyboardOptions = money,
                    )
                }
                item(key = "more") {
                    Column {
                        SectionHeader("Do you also keep money in")
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OnboardingWallets.forEach { FitakaChip(it.label, selected = it in picked, onClick = { onToggle(it) }) }
                        }
                    }
                }
                OnboardingWallets.filter { it in picked }.forEach { preset ->
                    item(key = "amount-${preset.name}") {
                        FitakaTextField(
                            state = amounts.getValue(preset), label = "In ${preset.label} now", placeholder = "0.00", prefix = Peso.SIGN,
                            isError = preset in problems,
                            supportingText = if (preset in problems) "Enter an amount, or leave it empty" else null,
                            inputTransformation = MoneyInput, keyboardOptions = money,
                        )
                    }
                }
                item(key = "later") {
                    Text(
                        "Other banks and wallets can be added later in Settings.",
                        style = type.bodyMedium, color = c.inkMuted, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            else -> {
                item(key = "ready") {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Alkansya(mood = AlkansyaMood.HAPPY, width = 168.dp)
                        Spacer(Modifier.height(12.dp))
                        Text("You are ready.", style = type.headlineSmall, color = c.ink, textAlign = TextAlign.Center)
                    }
                }
                item(key = "tips") {
                    FitakaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                        Tips.forEachIndexed { i, tip ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text("${i + 1}.", style = type.titleSmall, color = c.ink, modifier = Modifier.width(24.dp))
                                Text(tip, style = type.bodyMedium, color = c.ink)
                            }
                        }
                    }
                }
            }
        }
    }
}
