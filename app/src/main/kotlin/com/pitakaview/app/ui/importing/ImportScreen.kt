package com.pitakaview.app.ui.importing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pitakaview.app.APP_NAME
import com.pitakaview.app.data.db.dao.ImportBatchRow
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.domain.ImportSource
import com.pitakaview.app.domain.Peso
import com.pitakaview.app.domain.importing.ImportCandidate
import com.pitakaview.app.domain.importing.ImportStatus
import com.pitakaview.app.domain.importing.StatementWallet
import com.pitakaview.app.domain.importing.WalletFit
import com.pitakaview.app.ui.components.FitakaButton
import com.pitakaview.app.ui.components.FitakaConfirmDialog
import com.pitakaview.app.ui.components.FitakaButtonStyle
import com.pitakaview.app.ui.components.FitakaCard
import com.pitakaview.app.ui.components.FitakaChip
import com.pitakaview.app.ui.components.FitakaScreen
import com.pitakaview.app.ui.components.FitakaTextField
import com.pitakaview.app.ui.components.FitakaToggleRow
import com.pitakaview.app.ui.components.ProblemText
import com.pitakaview.app.ui.components.SectionHeader
import com.pitakaview.app.ui.components.sketchBox
import com.pitakaview.app.ui.icons.FitakaIcons
import com.pitakaview.app.ui.theme.FitakaDimens
import com.pitakaview.app.ui.theme.FitakaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The words of the Import screen, apart from the screen itself, so they can be checked without drawing it. */
object ImportText {
    private val DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

    fun entries(count: Int) = if (count == 1) "1 entry" else "$count entries"

    fun lines(count: Int) = if (count == 1) "1 line" else "$count lines"

    /** "23 lines read · 20 new · 2 already imported · 1 looks like an entry you already have". Only the kinds that occur are named. */
    fun summary(review: ImportStage.Review): String = buildList {
        add("${lines(review.candidates.size)} read")
        add("${review.count(ImportStatus.NEW)} new")
        review.count(ImportStatus.ALREADY_IMPORTED).takeIf { it > 0 }?.let { add("$it already imported") }
        review.count(ImportStatus.LOOKS_LIKE_EXISTING).takeIf { it > 0 }?.let { add("$it looks like an entry you already have") }
        review.count(ImportStatus.TWICE_IN_FILE).takeIf { it > 0 }?.let { add("$it twice in this file") }
        review.count(ImportStatus.OTHER_WALLET).takeIf { it > 0 }?.let { add("$it for another wallet") }
    }.joinToString(" · ")

    fun status(status: ImportStatus): String? = when (status) {
        ImportStatus.NEW -> null
        ImportStatus.ALREADY_IMPORTED -> "Already imported"
        ImportStatus.TWICE_IN_FILE -> "Twice in this file"
        ImportStatus.LOOKS_LIKE_EXISTING -> "Looks like an entry you already have"
        ImportStatus.OTHER_WALLET -> "For another wallet"
    }

    /** The sign says which way the money went, so a line whose direction is still a guess is shown without one. */
    fun amount(line: ImportCandidate): String = when {
        !line.row.sure -> Peso.format(line.row.amountCentavos)
        line.row.moneyIn -> "+${Peso.format(line.row.amountCentavos)}"
        else -> Peso.format(-line.row.amountCentavos)
    }

    fun day(line: ImportCandidate): String = DAY.format(line.row.date)

    fun notice(notice: ImportNotice): String = when (notice) {
        is ImportNotice.Imported -> "Imported ${entries(notice.count)} into ${notice.walletName}."
        is ImportNotice.Undone -> "Removed what ${notice.fileName} had added."
        is ImportNotice.Refused -> when (notice.why) {
            Refusal.TOO_BIG -> "That file is too big to be a statement (over 5 MB). Nothing was changed."
            Refusal.EMPTY -> "That file is empty, or could not be opened."
            Refusal.NO_TABLE -> "No table was found in that file. It needs a row of column names with a Date column and an Amount column (or Debit and Credit)."
            Refusal.MIXED_DATES -> "The dates in that file cannot be read for sure: some only work as day/month, others only as month/day. Nothing was changed."
            Refusal.NOTHING_IN_IT -> "The table in that file has no lines that can be read as entries."
            Refusal.PDF_NEEDS_NEWER_ANDROID -> "Opening a PDF statement needs Android 15 or later on this phone."
            Refusal.PDF_UNREADABLE -> "That PDF could not be opened."
            Refusal.COULD_NOT_SAVE -> "The import could not be saved. Nothing of that file was added."
            Refusal.NO_CATEGORY_IN_USE ->
                "Every Gastos category, or every Kita category, is hidden, so the lines of a file would have no category to go under. " +
                    "Show one again in Settings, under Categories, then choose the file again."
        }
    }

    fun batch(batch: ImportBatchRow, zone: ZoneId): String =
        "${entries(batch.entryCount)} · ${batch.walletName} · ${DAY.format(batch.importedAt.atZone(zone))}"

    const val CSV_HELP =
        "A CSV file works: a table with a Date column and an Amount column (or Debit and Credit). " +
            "Nothing is added until you have looked at the lines and tapped Import."
    const val PDF_HELP =
        "PDF statements from GCash and MariBank work too. A PDF from another bank cannot be brought in yet: choosing one " +
            "shows how it is laid out, with every letter and number hidden, which is what is needed to add that bank."

    const val UNSURE = "Check which way this went"

    /** How the lines compare with the statement's own totals, or null when the file gives none. */
    fun totals(review: ImportStage.Review): String? {
        val wanted = review.read.totals ?: return null
        val got = review.sums
        return if (review.addsUp == true) {
            "Adds up to the statement's own totals: ${Peso.format(got.moneyInCentavos)} in, ${Peso.format(got.moneyOutCentavos)} out."
        } else {
            "These lines do not add up to the statement's totals yet. Money in is ${Peso.format(got.moneyInCentavos)} of " +
                "${Peso.format(wanted.moneyInCentavos)}; money out is ${Peso.format(got.moneyOutCentavos)} of ${Peso.format(wanted.moneyOutCentavos)}. " +
                "Tap Change on a line to set which way it went. Import is off until they add up."
        }
    }

    /** Why Import is off although lines are ticked, or null when it is not. */
    fun unsure(review: ImportStage.Review): String? = review.unsure.takeIf { it > 0 }?.let {
        "${lines(it)} could have gone either way: the file does not say, and its totals fit both. " +
            "On ${if (it == 1) "the line" else "each line"} marked below, tap Money in or Money out. Import is off until then."
    }

    /**
     * Where a bank's statement is about to go, and how well that wallet fits the bank. Null for a file
     * that names no bank (a CSV), where there is nothing to compare the wallet with.
     */
    fun destination(source: ImportSource, wallets: List<WalletEntity>, walletId: Long?): Pair<String, WalletFit>? {
        val bank = StatementWallet.bankName(source) ?: return null
        val picked = wallets.firstOrNull { it.id == walletId } ?: return null
        val fit = StatementWallet.fit(bank, picked, wallets)
        val words = when (fit) {
            WalletFit.NAMED_FOR_BANK -> "This is a $bank statement. Its entries will go into ${picked.name}."
            WalletFit.ANOTHER_IS_NAMED_FOR_BANK ->
                "This is a $bank statement, but the wallet picked above is ${picked.name}. Tap your $bank wallet above if it belongs there."
            WalletFit.NONE_NAMED_FOR_BANK ->
                "This is a $bank statement, and its entries will go into ${picked.name}. If $bank should be a wallet of its own, " +
                    "tap Cancel and add it in Settings > Wallets first."
        }
        return words to fit
    }
    const val PDF_LAYOUT_HELP =
        "$APP_NAME cannot bring in entries from this PDF yet. Below is how it is laid out: every letter is shown as x " +
            "and every number as 9, so it says nothing about you or your money. Sending this to the developer is what is needed to add this bank."
    const val GCASH_PASSWORD_HINT = "For a GCash file: your last name in small letters, then the last 4 digits of your number."
}

/** Reads what was picked, stopping just past the size limit so a huge file is never loaded whole. Null if it cannot be opened. */
fun readPicked(resolver: ContentResolver, uri: Uri): PickedFile? = try {
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
    } ?: uri.lastPathSegment ?: "file"
    val bytes = resolver.openInputStream(uri)?.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (out.size() <= ImportViewModel.MAX_BYTES) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    }
    bytes?.let { PickedFile(name, it) }
} catch (e: IOException) {
    null
} catch (e: SecurityException) {
    null
}

@Composable
fun ImportRoute(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val vm = viewModel { ImportViewModel(container.database, container.ledger, container.pdfTextReader) }
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    val batches by vm.batches.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                // An unopenable file is handed on as an empty one, which the screen then says.
                vm.open(withContext(Dispatchers.IO) { readPicked(context.contentResolver, uri) } ?: PickedFile("file", ByteArray(0)))
            }
        }
    }
    if (!vm.loaded) {
        Box(Modifier.fillMaxSize().background(FitakaTheme.colors.background))
        return
    }
    ImportContent(
        stage = vm.stage, notice = vm.notice, wallets = wallets, walletId = vm.walletId, batches = batches,
        categories = { moneyIn -> if (moneyIn) vm.incomeCategories else vm.expenseCategories },
        openLine = vm.openLine,
        onWallet = vm::selectWallet,
        // Every kind of file is offered: CSV files turn up under several names, so the app checks the content itself.
        onChooseFile = { pick.launch(arrayOf("*/*")) },
        onToggle = vm::toggle, onShowChoices = vm::showChoices, onCategory = vm::setCategory, onDirection = vm::setDirection, onFlip = vm::flip,
        onImport = vm::importPicked, onCancel = vm::cancel, onUndo = vm::undo, onUnlockPdf = vm::unlockPdf,
        onCopy = { text ->
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("$APP_NAME statement layout", text))
        },
        onBack = onBack,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportContent(
    stage: ImportStage,
    notice: ImportNotice?,
    wallets: List<WalletEntity>,
    walletId: Long?,
    batches: List<ImportBatchRow>,
    categories: (moneyIn: Boolean) -> List<CategoryEntity>,
    openLine: Int?,
    onWallet: (Long) -> Unit,
    onChooseFile: () -> Unit,
    onToggle: (Int) -> Unit,
    onShowChoices: (Int) -> Unit,
    onCategory: (Int, Long) -> Unit,
    onDirection: (Int, Boolean) -> Unit,
    onFlip: () -> Unit,
    onImport: () -> Unit,
    onCancel: () -> Unit,
    onUndo: (ImportBatchRow) -> Unit,
    onUnlockPdf: (String) -> Unit,
    onCopy: (String) -> Unit,
    onBack: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val c = FitakaTheme.colors
    val type = MaterialTheme.typography
    val chips = Arrangement.spacedBy(8.dp)
    var undoId by rememberSaveable { mutableStateOf<Long?>(null) }
    val toUndo = batches.firstOrNull { it.id == undoId }
    val password = rememberTextFieldState()

    // Taking an import back out cannot be undone: asked over the whole screen, not in the footer.
    if (toUndo != null) {
        FitakaConfirmDialog(
            title = "Take ${toUndo.fileName} back out?",
            lines = listOf("The ${ImportText.entries(toUndo.entryCount)} it added will be removed. Entries you typed yourself stay."),
            confirmLabel = "Yes, remove",
            dismissLabel = "Keep",
            onConfirm = { onUndo(toUndo); undoId = null },
            onDismiss = { undoId = null },
        )
    }

    FitakaScreen(
        title = "Import",
        subtitle = "Bring in entries from a file",
        onBack = onBack,
        pinnedAction = when {
            stage is ImportStage.Review -> {
                {
                    Column {
                        Text(ImportText.summary(stage), style = type.bodySmall, color = c.ink)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FitakaButton("Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
                            FitakaButton(
                                "Import ${ImportText.entries(stage.picked)}", onClick = onImport, modifier = Modifier.weight(2f),
                                style = FitakaButtonStyle.Primary, enabled = stage.canImport,
                            )
                        }
                    }
                }
            }
            else -> null
        },
    ) {
        if (notice != null) {
            item(key = "notice") {
                FitakaCard(Modifier.fillMaxWidth()) { Text(ImportText.notice(notice), style = type.bodyMedium, color = c.ink) }
            }
        }
        item(key = "wallet") {
            Column {
                SectionHeader("Which wallet is the file for")
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = chips, verticalArrangement = chips) {
                    wallets.forEach { FitakaChip(it.name, selected = it.id == walletId, onClick = { onWallet(it.id) }) }
                }
            }
        }
        when (stage) {
            ImportStage.Choose -> {
                item(key = "choose") {
                    FitakaButton(
                        "Choose a file", onClick = onChooseFile, modifier = Modifier.fillMaxWidth(),
                        style = FitakaButtonStyle.Primary, enabled = walletId != null,
                    )
                }
                item(key = "help") {
                    Column(Modifier.padding(horizontal = 4.dp)) {
                        Text(ImportText.CSV_HELP, style = type.bodyMedium, color = c.inkMuted)
                        Spacer(Modifier.height(6.dp))
                        Text(ImportText.PDF_HELP, style = type.bodyMedium, color = c.inkMuted)
                    }
                }
                if (batches.isNotEmpty()) {
                    item(key = "batches-title") { SectionHeader("Earlier imports") }
                    items(batches.size, key = { "batch-${batches[it].id}" }) { index ->
                        val batch = batches[index]
                        FitakaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(batch.fileName, style = type.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(ImportText.batch(batch, zone), style = type.bodyMedium, color = c.inkMuted)
                            Spacer(Modifier.height(8.dp))
                            FitakaButton("Take it back out", onClick = { undoId = batch.id }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            is ImportStage.Review -> {
                item(key = "file") {
                    Column(Modifier.padding(horizontal = 4.dp)) {
                        Text(stage.fileName, style = type.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Tick the lines to bring in. Nothing is added until you tap Import.", style = type.bodyMedium, color = c.inkMuted)
                        // A bank's statement belongs in that bank's wallet; say where this one is headed before it goes.
                        ImportText.destination(stage.read.source, wallets, walletId)?.let { (words, fit) ->
                            if (fit == WalletFit.NAMED_FOR_BANK) Text(words, style = type.bodySmall, color = c.inkMuted) else ProblemText(words)
                        }
                        if (stage.read.unreadLines > 0) {
                            ProblemText("${ImportText.lines(stage.read.unreadLines)} could not be read as an entry and ${if (stage.read.unreadLines == 1) "is" else "are"} left out.")
                        }
                        if (stage.read.movesLeftOut > 0) {
                            Text("${ImportText.lines(stage.read.movesLeftOut)} moving money between your own wallets ${if (stage.read.movesLeftOut == 1) "is" else "are"} left out.", style = type.bodySmall, color = c.inkMuted)
                        }
                        if (stage.read.datesAssumedMonthFirst) {
                            Text("Dates like 03/04/2026 were read month first (March 4). Check the dates below.", style = type.bodySmall, color = c.inkMuted)
                        }
                        // A bank statement says what all its lines must add up to; that is checked before anything is added.
                        ImportText.totals(stage)?.let { words ->
                            if (stage.addsUp == true) Text(words, style = type.bodySmall, color = c.inkMuted) else ProblemText(words)
                        }
                        ImportText.unsure(stage)?.let { ProblemText(it) }
                    }
                }
                if (!stage.read.directionKnown) {
                    item(key = "flip") {
                        FitakaToggleRow(
                            title = "These amounts are money coming in",
                            subtitle = "The file does not say which way the money went. Off means money you spent.",
                            checked = stage.flipped,
                            onCheckedChange = { onFlip() },
                        )
                    }
                }
                items(stage.candidates.size, key = { "line-$it" }) { index ->
                    val line = stage.candidates[index]
                    val choices = categories(line.row.moneyIn)
                    CandidateLine(
                        line = line,
                        categoryName = choices.firstOrNull { it.id == line.categoryId }?.name.orEmpty(),
                        open = openLine == index,
                        choices = choices,
                        onToggle = { onToggle(index) },
                        onShowChoices = { onShowChoices(index) },
                        onCategory = { onCategory(index, it) },
                        onDirection = { onDirection(index, it) },
                    )
                }
            }
            is ImportStage.PdfLocked -> {
                item(key = "locked") {
                    Column {
                        Text(stage.file.name, style = type.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("This file is locked. Type its password to open it.", style = type.bodyMedium, color = c.ink)
                    }
                }
                item(key = "password") {
                    FitakaTextField(
                        state = password, label = "Password of the file",
                        isError = stage.wrongPassword,
                        supportingText = if (stage.wrongPassword) "That password did not open the file" else ImportText.GCASH_PASSWORD_HINT,
                        // A password keyboard keeps the phone from learning or suggesting what is typed.
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
                item(key = "open") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FitakaButton("Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
                        FitakaButton("Open", onClick = { onUnlockPdf(password.text.toString()) }, modifier = Modifier.weight(1f), style = FitakaButtonStyle.Primary)
                    }
                }
            }
            is ImportStage.PdfLayout -> {
                item(key = "layout-help") {
                    Column(Modifier.padding(horizontal = 4.dp)) {
                        Text("${stage.fileName} · ${if (stage.pages == 1) "1 page" else "${stage.pages} pages"}", style = type.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(ImportText.PDF_LAYOUT_HELP, style = type.bodyMedium, color = c.inkMuted)
                    }
                }
                item(key = "layout-actions") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FitakaButton("Done", onClick = onCancel, modifier = Modifier.weight(1f))
                        FitakaButton("Copy the layout", onClick = { onCopy(stage.sample) }, modifier = Modifier.weight(2f), style = FitakaButtonStyle.Primary)
                    }
                }
                item(key = "layout") {
                    FitakaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                        Text(
                            stage.sample,
                            style = type.bodySmall.copy(fontFamily = FontFamily.Monospace), color = c.ink,
                            softWrap = false,
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CandidateLine(
    line: ImportCandidate,
    categoryName: String,
    /** Whether this line's choices (which way it went, which category) are showing. */
    open: Boolean,
    /** The categories that fit the line's direction. */
    choices: List<CategoryEntity>,
    onToggle: () -> Unit,
    onShowChoices: () -> Unit,
    onCategory: (Long) -> Unit,
    onDirection: (Boolean) -> Unit,
) {
    val c = FitakaTheme.colors
    val type = MaterialTheme.typography
    val ticked = line.picked && line.canPick
    val chips = Arrangement.spacedBy(8.dp)
    // Which way the money went. Picking either settles a line the file left open.
    val direction: @Composable () -> Unit = {
        FlowRow(horizontalArrangement = chips, verticalArrangement = chips) {
            FitakaChip("Money out", selected = !line.row.moneyIn && line.row.sure, onClick = { onDirection(false) })
            FitakaChip("Money in", selected = line.row.moneyIn && line.row.sure, onClick = { onDirection(true) })
        }
    }
    FitakaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = ticked, enabled = line.canPick, role = Role.Checkbox, onValueChange = { onToggle() })
                .heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(26.dp).sketchBox(RoundedCornerShape(7.dp), if (ticked) c.ink else c.card, if (line.canPick) c.ink else c.inkMuted, borderWidth = FitakaDimens.borderThin),
                contentAlignment = Alignment.Center,
            ) {
                if (ticked) Icon(FitakaIcons.Check, contentDescription = null, tint = c.card, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    line.row.description.ifBlank { "(no description)" },
                    style = type.titleSmall, color = if (line.canPick) c.ink else c.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(ImportText.day(line), ImportText.status(line.status)).joinToString(" · "),
                    style = type.bodySmall, color = c.inkMuted,
                )
                if (!line.row.sure) ProblemText(ImportText.UNSURE, Modifier.padding(start = 0.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(ImportText.amount(line), style = type.titleSmall, color = if (line.canPick) c.ink else c.inkMuted)
        }
        // A line the file left open asks its question right here, where the warning is: the answer must
        // not sit behind another tap, and a tap on the line itself only ticks or unticks it. Asked even
        // on a line that cannot be ticked, because its direction still counts towards the totals.
        if (!line.row.sure) {
            Box(Modifier.padding(start = 38.dp, top = 4.dp, bottom = 4.dp)) { direction() }
        }
        if (line.canPick) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = "Change this line", onClick = onShowChoices).padding(start = 38.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Category: $categoryName", style = type.bodyMedium, color = c.ink, modifier = Modifier.weight(1f))
                Text(if (open) "Close" else "Change", style = type.labelLarge, color = c.ink)
            }
        }
        if (open && line.canPick) {
            Column(Modifier.padding(start = 38.dp, bottom = 8.dp)) {
                // A settled line can still be turned round here, if the owner knows better.
                if (line.row.sure) {
                    direction()
                    Spacer(Modifier.height(8.dp))
                }
                FlowRow(horizontalArrangement = chips, verticalArrangement = chips) {
                    choices.forEach { FitakaChip(it.name, selected = it.id == line.categoryId, onClick = { onCategory(it.id) }) }
                }
            }
        }
    }
}
