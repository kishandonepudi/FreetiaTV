package com.freetia.tv.ui.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetia.tv.data.backup.BackupData
import com.freetia.tv.data.backup.BackupFiles
import com.freetia.tv.data.backup.BackupOpen
import com.freetia.tv.data.backup.BackupService
import com.freetia.tv.data.backup.ExportOptions
import com.freetia.tv.data.backup.ImportMode
import com.freetia.tv.data.backup.ImportPlanner
import com.freetia.tv.data.backup.SyncCode
import com.freetia.tv.data.backup.lines
import com.freetia.tv.ui.components.BrandDialog
import com.freetia.tv.ui.components.FocusPill
import com.freetia.tv.ui.components.LocalBrandToast
import com.freetia.tv.ui.components.SettingActionRow
import com.freetia.tv.ui.components.SettingSectionHeader
import com.freetia.tv.ui.components.SettingToggleRow
import com.freetia.tv.ui.components.SettingsScaffold
import com.freetia.tv.ui.nav.Navigator
import com.freetia.tv.ui.safety.rememberSafetyActions
import com.freetia.tv.ui.theme.LocalBrand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MIN_PASSPHRASE = 6
private const val MAX_TYPED_CODE = 700

private sealed interface BackupDialog {
    data object Passphrase : BackupDialog
    data class ImportPassphrase(val text: String) : BackupDialog
    data object ImportCode : BackupDialog
    data class Summary(val data: BackupData, val mode: ImportMode, val restorePin: Boolean) : BackupDialog
    data class Qr(val code: String) : BackupDialog
}

/** Settings > Advanced > Export and import (§5). Everything is a local file or code; there is no server. */
@Composable
fun BackupScreen(nav: Navigator) {
    val c = rememberSettingsCtx()
    val context = LocalContext.current
    val toast = LocalBrandToast.current
    val actions = rememberSafetyActions()
    val service = remember { BackupService(c.app) }
    var passphrase by remember { mutableStateOf("") }
    var includeLibrary by remember { mutableStateOf(true) }
    var includePin by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<BackupDialog?>(null) }
    var busy by remember { mutableStateOf(false) }
    val encrypted = passphrase.isNotEmpty()

    suspend fun openText(text: String?, pass: String) {
        if (text == null) { toast.show("That file is too large or could not be read.", isError = true); return }
        val result = try {
            busy = true
            withContext(Dispatchers.Default) { BackupFiles.open(text, pass.toCharArray().takeIf { it.isNotEmpty() }) }
        } finally { busy = false }
        when (result) {
            is BackupOpen.Ok -> dialog = BackupDialog.Summary(result.data, ImportMode.MERGE, restorePin = false)
            BackupOpen.NeedsPassphrase -> dialog = BackupDialog.ImportPassphrase(text)
            is BackupOpen.Invalid -> toast.show(result.reason, isError = true)
        }
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        c.launch {
            try {
                val data = service.build(ExportOptions(includeLibrary, passphrase.toCharArray().takeIf { it.isNotEmpty() }, includePin && encrypted))
                busy = true
                val text = try {
                    withContext(Dispatchers.Default) { BackupFiles.write(data, passphrase.toCharArray().takeIf { it.isNotEmpty() }) }
                } finally { busy = false }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: error("no stream")
                }
                toast.show(if (encrypted) "Backup saved, encrypted." else "Backup saved. Playlist links were left out.")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                toast.show("The backup could not be saved.", isError = true)
            }
        }
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        c.launch {
            val text = try {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use(BackupFiles::readBounded) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            openText(text, passphrase)
        }
    }
    val noPicker = "This device has no file picker. Use the QR code or a code instead."

    SettingsScaffold("Export and import", subtitle = "Files stay on your device or wherever you save them.") {
        item { SettingSectionHeader("Save a backup") }
        item {
            SettingActionRow(
                "Passphrase", onClick = { dialog = BackupDialog.Passphrase },
                subtitle = "With a passphrase the file is encrypted and includes your playlist links. Without one, links are left out.",
                value = if (encrypted) "Set" else "None",
                modifier = Modifier.describe("Passphrase", if (encrypted) "Set" else "None"),
            )
        }
        item {
            SettingToggleRow(
                "Include favourites, groups and locks", includeLibrary, { includeLibrary = it },
                modifier = Modifier.describe("Include favourites, groups and locks"),
            )
        }
        item {
            SettingToggleRow(
                "Include my PIN", includePin && encrypted,
                { on ->
                    if (!on) includePin = false
                    else c.launchPinned(actions, "Enter your PIN to include it") { includePin = true }
                },
                subtitle = if (encrypted) "The PIN is stored only as a scrambled hash" else "Set a passphrase first",
                enabled = encrypted, modifier = Modifier.describe("Include my PIN"),
            )
        }
        item {
            SettingActionRow(
                "Save to a file", showChevron = false,
                onClick = {
                    val pick = { try { saver.launch("freetia-backup.json") } catch (e: ActivityNotFoundException) { toast.show(noPicker, isError = true) } }
                    if (encrypted) c.launchPinned(actions, "Enter your PIN to export your links", pick) else pick()
                },
                modifier = Modifier.describe("Save a backup to a file"),
            )
        }
        item { SettingSectionHeader("Restore") }
        item {
            SettingActionRow(
                "Import from a file", showChevron = false,
                onClick = {
                    try { opener.launch(arrayOf("*/*")) } catch (e: ActivityNotFoundException) { toast.show(noPicker, isError = true) }
                },
                subtitle = "You will see a summary before anything changes",
                modifier = Modifier.describe("Import from a file"),
            )
        }
        item {
            SettingActionRow(
                "Import from a code", onClick = { dialog = BackupDialog.ImportCode },
                subtitle = "Type or paste a code made on another device", modifier = Modifier.describe("Import from a code"),
            )
        }
        item { SettingSectionHeader("Move to another device") }
        item {
            SettingActionRow(
                "Show QR code and code", showChevron = false,
                subtitle = "Carries settings and playlist names. Links only travel when a passphrase is set.",
                onClick = {
                    val export = {
                        c.launch {
                            try {
                                val pass = passphrase.toCharArray().takeIf { it.isNotEmpty() }
                                val data = service.buildCompact(pass)
                                busy = true
                                val text = try {
                                    withContext(Dispatchers.Default) { BackupFiles.write(data, pass) }
                                } finally { busy = false }
                                dialog = BackupDialog.Qr(SyncCode.encode(text))
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                toast.show("The code could not be made.", isError = true)
                            }
                        }
                    }
                    if (encrypted) c.launchPinned(actions, "Enter your PIN to export your links", export) else export()
                },
                modifier = Modifier.describe("Show a QR code and code for another device"),
            )
        }
    }

    if (busy) {
        BrandDialog(
            title = "Working",
            message = "Protecting your backup with the passphrase. This can take a few seconds.",
            confirmLabel = "Please wait",
            dismissLabel = null,
            onDismiss = {},
        )
    }

    when (val d = dialog) {
        null -> Unit
        BackupDialog.Passphrase -> TextPromptDialog(
            title = "Backup passphrase",
            label = "Passphrase",
            initial = passphrase,
            confirmLabel = "Save",
            message = "At least $MIN_PASSPHRASE characters. Leave it empty for a file without links. If you forget it, the file cannot be opened.",
            allowBlank = true,
            password = true,
            validate = { if (it.length >= MIN_PASSPHRASE) null else "Use at least $MIN_PASSPHRASE characters." },
            onConfirm = {
                passphrase = it
                if (it.isEmpty()) includePin = false
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        is BackupDialog.ImportPassphrase -> TextPromptDialog(
            title = "This backup is encrypted",
            label = "Passphrase",
            initial = "",
            confirmLabel = "Open",
            password = true,
            onConfirm = { pass ->
                dialog = null
                c.launch { openText(d.text, pass) }
            },
            onDismiss = { dialog = null },
        )
        BackupDialog.ImportCode -> TextPromptDialog(
            title = "Import from a code",
            label = "Code",
            initial = "",
            confirmLabel = "Continue",
            message = "Spaces and dashes do not matter.",
            maxLength = 40_000,
            onConfirm = { code ->
                dialog = null
                val json = SyncCode.decode(code)
                if (json == null) toast.show("That code is not valid.", isError = true)
                else c.launch { openText(json, passphrase) }
            },
            onDismiss = { dialog = null },
        )
        is BackupDialog.Summary -> SummaryDialog(
            state = d,
            service = service,
            onChange = { dialog = it },
            onDismiss = { dialog = null },
            onApply = {
                dialog = null
                c.launchPinned(actions, "Enter your PIN to import") {
                    c.launch {
                        try {
                            val r = service.apply(d.data, d.mode, d.restorePin)
                            val extra = if (r.favouritesTotal > 0) " ${r.favouritesMatched} of ${r.favouritesTotal} favourites matched." else ""
                            toast.show("Import done.$extra")
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            toast.show("The import could not be finished.", isError = true)
                        }
                    }
                }
            },
        )
        is BackupDialog.Qr -> QrDialog(d.code, onDismiss = { dialog = null })
    }
}

@Composable
private fun SummaryDialog(
    state: BackupDialog.Summary,
    service: BackupService,
    onChange: (BackupDialog) -> Unit,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
) {
    val names by androidx.compose.runtime.produceState(emptyList<String>()) { value = service.existingNames() }
    val summary = ImportPlanner.summarize(state.data, names, state.mode)
    BrandDialog(
        title = "Import this backup?",
        message = summary.lines().joinToString("\n"),
        confirmLabel = "Import",
        onConfirm = onApply,
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusPill("Merge", { onChange(state.copy(mode = ImportMode.MERGE)) }, primary = state.mode == ImportMode.MERGE)
                FocusPill("Replace playlists", { onChange(state.copy(mode = ImportMode.REPLACE)) }, primary = state.mode == ImportMode.REPLACE)
            }
            Text(
                if (state.mode == ImportMode.MERGE) "Keeps what you have and adds the rest."
                else "Playlists not in the file are removed. Favourites, groups and locks are always merged.",
                fontSize = 13.sp, color = LocalBrand.current.textMuted,
            )
            if (summary.includesPin) {
                FocusPill(
                    if (state.restorePin) "PIN will be restored" else "Restore the PIN too",
                    { onChange(state.copy(restorePin = !state.restorePin)) }, primary = state.restorePin,
                )
            }
        }
    }
}

@Composable
private fun QrDialog(code: String, onDismiss: () -> Unit) {
    val matrix = remember(code) {
        try {
            if (code.length <= SyncCode.MAX_QR_CHARS) SyncCode.qrMatrix(code) else null
        } catch (e: Exception) {
            null
        }
    }
    BrandDialog(
        title = "Move to another device",
        message = when {
            matrix == null -> "This is too much to show as a QR code. Save a file instead."
            else -> "Scan this with any QR scanner on a phone, copy the text, then choose Import from a code on the other device."
        },
        confirmLabel = "Done",
        dismissLabel = null,
        onConfirm = onDismiss,
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (matrix != null) {
                Canvas(
                    Modifier
                        .size(220.dp)
                        .aspectRatio(1f)
                        .background(Color.White)
                        .semantics { contentDescription = "QR code carrying your settings" },
                ) {
                    val n = matrix.width
                    val cell = size.width / n
                    for (y in 0 until matrix.height) for (x in 0 until n) {
                        if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.6f, cell + 0.6f))
                    }
                }
            }
            if (code.length <= MAX_TYPED_CODE) {
                Text(SyncCode.grouped(code), fontSize = 14.sp, color = LocalBrand.current.textPrimary)
            }
        }
    }
}
