package com.pitakaview.app.ui.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pitakaview.app.APP_NAME
import com.pitakaview.app.data.backup.BackupCheck
import com.pitakaview.app.data.backup.BackupInfo
import com.pitakaview.app.data.backup.BackupManager
import com.pitakaview.app.data.backup.RestoreNotStartedException
import com.pitakaview.app.data.db.dao.TransactionDao
import com.pitakaview.app.data.prefs.SettingsRepository
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.domain.CsvExport
import com.pitakaview.app.ui.components.FitakaButton
import com.pitakaview.app.ui.components.FitakaConfirmDialog
import com.pitakaview.app.ui.components.FitakaButtonStyle
import com.pitakaview.app.ui.components.FitakaCard
import com.pitakaview.app.ui.components.FitakaScreen
import com.pitakaview.app.ui.components.ProblemText
import com.pitakaview.app.ui.components.SectionHeader
import com.pitakaview.app.ui.home.friendlyDate
import com.pitakaview.app.ui.ledger.entriesText
import com.pitakaview.app.ui.theme.FitakaTheme
import com.pitakaview.app.ui.theme.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val TAG = "FitakaBackup"

/**
 * The names offered when saving. The owner chose `fitaka-backup-<date>` on 2026-11-27. The ending stays
 * `.pitaka`, as on every backup made while the app was called PitakaView: a restore never looks at a
 * file's name or ending, only at what is inside it, so old and new backups are picked the same way.
 */
internal fun backupFileName(day: LocalDate) = "fitaka-backup-$day.pitaka"

internal fun sheetFileName(day: LocalDate) = "fitaka-entries-$day.csv"

/** What the Backup screen tells the owner after an action. */
enum class BackupNotice { SAVED, SAVE_FAILED, NOT_A_BACKUP, TOO_NEW, RESTORE_FAILED, EXPORTED, EXPORT_FAILED }

class BackupViewModel(
    private val backup: BackupManager,
    private val settings: SettingsRepository,
    private val entries: TransactionDao,
    workDir: File,
    private val restartApp: () -> Unit,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

    val lastBackupAt: StateFlow<Instant?> = settings.lastBackupAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var notice by mutableStateOf<BackupNotice?>(null)
        private set

    /** A backup file that passed inspection and is waiting for the owner's yes or no. */
    var pending by mutableStateOf<BackupInfo?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    /** How many entries the last spreadsheet export wrote. */
    var exportedCount by mutableStateOf(0)
        private set

    private val candidate = File(workDir, "restore-candidate.db")

    /**
     * [open] gives the place the owner chose to save the file. If the write fails after that place was
     * opened, [discardHalfWritten] removes what is there: a cut-off file would look like a backup and
     * only fail on the day it is needed.
     */
    fun backUpTo(open: () -> OutputStream?, discardHalfWritten: () -> Unit = {}) {
        if (busy) return
        busy = true
        notice = null
        viewModelScope.launch {
            // Leaving the screen must not cut a backup short: it finishes and is recorded either way.
            notice = withContext(NonCancellable) {
                var opened = false
                try {
                    backup.exportTo {
                        checkNotNull(open()) { "The chosen place could not be opened for writing" }.also { opened = true }
                    }
                    // Only a finished file counts as a backup.
                    settings.setLastBackupAt(now())
                    BackupNotice.SAVED
                } catch (e: Exception) {
                    Log.e(TAG, "Backup could not be written", e)
                    if (opened) withContext(Dispatchers.IO) { runCatching(discardHalfWritten) }
                    BackupNotice.SAVE_FAILED
                }
            }
            busy = false
        }
    }

    /**
     * Writes every entry as a spreadsheet file (CSV) to the place the owner chose. As with a backup, a
     * file that breaks halfway is removed rather than left looking complete.
     */
    fun exportTo(open: () -> OutputStream?, discardHalfWritten: () -> Unit = {}) {
        if (busy) return
        busy = true
        notice = null
        viewModelScope.launch {
            // Leaving the screen must not cut the file short.
            notice = withContext(NonCancellable) {
                var opened = false
                try {
                    exportedCount = withContext(Dispatchers.IO) {
                        // Read first, so a failure here never empties a file the owner chose to overwrite.
                        val rows = entries.exportRows()
                        val out = checkNotNull(open()) { "The chosen place could not be opened for writing" }
                        opened = true
                        out.bufferedWriter(Charsets.UTF_8).use { writer -> CsvExport.write(rows, writer) { it.label } }
                    }
                    BackupNotice.EXPORTED
                } catch (e: Exception) {
                    Log.e(TAG, "Spreadsheet could not be written", e)
                    if (opened) withContext(Dispatchers.IO) { runCatching(discardHalfWritten) }
                    BackupNotice.EXPORT_FAILED
                }
            }
            busy = false
        }
    }

    /** Copies the chosen file into the app and inspects it. Nothing is replaced yet. */
    fun stageRestore(open: () -> InputStream?) {
        if (busy) return
        busy = true
        notice = null
        pending = null
        viewModelScope.launch {
            val check = withContext(Dispatchers.IO) {
                try {
                    val input = checkNotNull(open()) { "The chosen file could not be opened" }
                    if (input.use { backup.stage(it, candidate) }) backup.inspect(candidate) else BackupCheck.NotABackup
                } catch (e: Exception) {
                    Log.e(TAG, "The chosen backup file could not be read", e)
                    BackupCheck.NotABackup
                }
            }
            when (check) {
                is BackupCheck.Ok -> pending = check.info
                BackupCheck.NotABackup -> notice = BackupNotice.NOT_A_BACKUP
                BackupCheck.TooNew -> notice = BackupNotice.TOO_NEW
            }
            if (check !is BackupCheck.Ok) withContext(Dispatchers.IO) { candidate.delete() }
            busy = false
        }
    }

    fun cancelRestore() {
        pending = null
        viewModelScope.launch(Dispatchers.IO) { candidate.delete() }
    }

    /** Replaces everything with the staged backup, then restarts the app. */
    fun confirmRestore() {
        if (busy || pending == null) return
        busy = true
        viewModelScope.launch {
            // Leaving the screen must not stop this between the swap and the restart: after the swap,
            // everything in memory points at a closed database, and only a restart puts that right.
            withContext(NonCancellable) {
                try {
                    backup.replaceDatabaseWith(candidate)
                } catch (e: RestoreNotStartedException) {
                    Log.e(TAG, "Restore did not start; nothing was changed", e)
                    notice = BackupNotice.RESTORE_FAILED
                    pending = null
                    busy = false
                    return@withContext
                } catch (e: Exception) {
                    // The database is closed by now, so the app cannot carry on as it is. The old data is still on disk.
                    Log.e(TAG, "Restore failed after the database was closed; restarting on the old data", e)
                    // The restart wipes everything in memory, so what happened is written down first and
                    // said on Home afterwards. Settings live in their own file, apart from the database.
                    try {
                        settings.setRestoreFailed(true)
                    } catch (noted: Exception) {
                        Log.e(TAG, "Could not note the failed restore; the app restarts without the message", noted)
                    }
                }
                restartApp()
            }
        }
    }
}

@Composable
fun BackupRoute(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val vm = viewModel {
        BackupViewModel(container.backup, container.settings, container.database.transactionDao(), context.cacheDir, restartApp = { container.restartApp() })
    }
    val last by vm.lastBackupAt.collectAsStateWithLifecycle()
    val today by container.dayClock.today.collectAsStateWithLifecycle()
    // The app's own resolver, so a long save does not hold on to this screen.
    val resolver = context.applicationContext.contentResolver

    // Android's own "save a file" and "pick a file" screens: the owner chooses where the backup goes.
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            vm.backUpTo(
                open = { resolver.openForOverwrite(uri) },
                discardHalfWritten = { DocumentsContract.deleteDocument(resolver, uri) },
            )
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.stageRestore { resolver.openInputStream(uri) }
    }
    val exportSheet = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            vm.exportTo(
                open = { resolver.openForOverwrite(uri) },
                discardHalfWritten = { DocumentsContract.deleteDocument(resolver, uri) },
            )
        }
    }

    BackupContent(
        lastBackupOn = last?.atZone(ZoneId.systemDefault())?.toLocalDate(),
        today = today,
        notice = vm.notice,
        pending = vm.pending,
        busy = vm.busy,
        onBackUp = { save.launch(backupFileName(today)) },
        onPickBackup = { pick.launch(arrayOf("*/*")) },
        onExport = { exportSheet.launch(sheetFileName(today)) },
        exportedCount = vm.exportedCount,
        onConfirmRestore = vm::confirmRestore,
        onCancelRestore = vm::cancelRestore,
        onBack = onBack,
    )
}

/**
 * Opens the chosen document so that any old content is cut off. Since Android 10 plain "w" may leave
 * the tail of an older, longer file in place; "wt" asks for it to be emptied, and a few storage
 * providers only accept the "rwt" spelling.
 */
private fun ContentResolver.openForOverwrite(uri: Uri): OutputStream? =
    try {
        openOutputStream(uri, "wt")
    } catch (e: Exception) {
        Log.w(TAG, "This place does not accept \"wt\"; trying \"rwt\"", e)
        openOutputStream(uri, "rwt")
    }

@Composable
fun BackupContent(
    lastBackupOn: LocalDate?,
    today: LocalDate,
    notice: BackupNotice?,
    pending: BackupInfo?,
    busy: Boolean,
    onBackUp: () -> Unit,
    onPickBackup: () -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    onBack: () -> Unit,
    onExport: () -> Unit = {},
    exportedCount: Int = 0,
) {
    val c = FitakaTheme.colors
    val type = MaterialTheme.typography
    // Replacing everything cannot be undone: asked over the whole screen, not in the footer.
    if (pending != null) {
        FitakaConfirmDialog(
            title = "Replace everything on this phone?",
            lines = listOf(describe(pending, today), "What is on this phone now will be removed. This cannot be undone."),
            confirmLabel = "Yes, restore",
            dismissLabel = "Cancel",
            onConfirm = onConfirmRestore,
            onDismiss = onCancelRestore,
            enabled = !busy,
        )
    }

    FitakaScreen(
        title = "Backup",
        subtitle = "Keep a copy of your data",
        onBack = onBack,
    ) {
        item(key = "why") {
            FitakaCard(Modifier.fillMaxWidth()) {
                Text("Your data lives only on this phone", style = type.titleSmall, color = c.ink)
                Text(
                    "A backup is one file. Keep it somewhere safe, like Google Drive or an email to yourself. " +
                        "If you lose or change your phone, you can bring everything back from it.",
                    style = type.bodyMedium, color = c.inkMuted,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (lastBackupOn == null) "Never backed up" else "Last backup: ${friendlyDate(lastBackupOn, today)}",
                    style = type.labelLarge, color = c.ink,
                )
            }
        }
        item(key = "save") {
            Column {
                FitakaButton(
                    "Back up now", onClick = onBackUp, modifier = Modifier.fillMaxWidth(),
                    style = FitakaButtonStyle.Primary, enabled = !busy && pending == null,
                )
                when (notice) {
                    BackupNotice.SAVED -> Text(
                        "Backup saved. Keep that file somewhere safe.",
                        style = type.bodyMedium, color = c.ink, modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                    BackupNotice.SAVE_FAILED -> ProblemText("The backup could not be saved. Nothing was changed; please try another place.")
                    else -> Unit
                }
            }
        }
        item(key = "restore") {
            Column {
                FitakaButton("Restore from a backup", onClick = onPickBackup, modifier = Modifier.fillMaxWidth(), enabled = !busy && pending == null)
                when (notice) {
                    BackupNotice.NOT_A_BACKUP -> ProblemText("That file is not a $APP_NAME backup, or it is damaged. Nothing was changed.")
                    BackupNotice.TOO_NEW -> ProblemText("That backup was made by a newer version of $APP_NAME. Update the app, then try again.")
                    BackupNotice.RESTORE_FAILED -> ProblemText("The restore did not finish. Your current data is still here.")
                    else -> Unit
                }
            }
        }
        item(key = "private") {
            Text(
                "The backup file is not locked with a password, so keep it private.",
                style = type.bodySmall, color = c.inkMuted, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        item(key = "export-title") { SectionHeader("Spreadsheet") }
        item(key = "export") {
            Column {
                FitakaButton("Export to a spreadsheet", onClick = onExport, modifier = Modifier.fillMaxWidth(), enabled = !busy && pending == null)
                when (notice) {
                    BackupNotice.EXPORTED -> Text(
                        "Exported ${entriesText(exportedCount)}.",
                        style = type.bodyMedium, color = c.ink, modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                    BackupNotice.EXPORT_FAILED -> ProblemText("The spreadsheet could not be saved. Please try another place.")
                    else -> Unit
                }
                Text(
                    "A CSV file of every entry, for Excel or Google Sheets. It is for reading only: a spreadsheet cannot be restored, so keep making backups.",
                    style = type.bodySmall, color = c.inkMuted, modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
        }
    }
}

private fun describe(info: BackupInfo, today: LocalDate): String {
    val entries = if (info.entries == 1) "1 entry" else "${info.entries} entries"
    val wallets = if (info.wallets == 1) "1 wallet" else "${info.wallets} wallets"
    val last = info.lastEntryOn?.let { ", last entry ${friendlyDate(it, today)}" } ?: ""
    return "The backup holds $entries in $wallets$last."
}
