package net.perfectdreams.butterscotch.android.components

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.perfectdreams.butterscotch.android.layouts.GamepadLayout
import net.perfectdreams.butterscotch.android.layouts.LayoutLibrary
import net.perfectdreams.butterscotch.android.library.GameEntry
import net.perfectdreams.butterscotch.android.pe.IconCandidate
import java.util.UUID

/**
 * A shared game metadata editor.
 */
@Composable
fun MetadataForm(
    layoutLibrary: LayoutLibrary,
    state: GameMetadataFormState,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    loadCandidates: suspend () -> List<IconCandidate>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickerOpen by remember { mutableStateOf(false) }

    val pickCustom = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null)
            return@rememberLauncherForActivityResult

        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodeUriToBitmap(context, uri) }
            if (bitmap != null) state.selectedIcon = bitmap
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        OutlinedTextField(
            value = state.title,
            onValueChange = { state.title = it },
            label = { Text("Title") },
            singleLine = true,
            isError = state.title.isBlank(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        IconSection(
            selected = state.selectedIcon,
            onClick = {
                pickerOpen = true
            },
        )

        LayoutDropdown(
            label = "Portrait Layout",
            selectedId = state.portraitLayout,
            options = layoutLibrary.entries.filter { it.orientation == GamepadLayout.GamepadTargetOrientation.PORTRAIT },
            onSelect = { id -> state.portraitLayout = id },
        )

        Spacer(Modifier.height(16.dp))

        LayoutDropdown(
            label = "Landscape Layout",
            selectedId = state.landscapeLayout,
            options = layoutLibrary.entries.filter { it.orientation == GamepadLayout.GamepadTargetOrientation.LANDSCAPE },
            onSelect = { id -> state.landscapeLayout = id },
        )

        Spacer(Modifier.height(16.dp))

        OsDropdown(
            selected = state.runnerOs,
            onSelect = { state.runnerOs = it },
        )

        Spacer(Modifier.height(16.dp))

        InputToggle(
            title = "Enable physical controllers",
            subtitle = null,
            checked = state.enablePhysicalControllers,
            onChange = { state.enablePhysicalControllers = it },
        )

        Spacer(Modifier.height(16.dp))

        InputToggle(
            title = "Enable physical keyboard",
            subtitle = null,
            checked = state.enablePhysicalKeyboard,
            onChange = { state.enablePhysicalKeyboard = it },
        )

        Spacer(Modifier.height(24.dp))

        InputToggle(
            title = "Enable Widescreen Hack",
            subtitle = "May cause visual glitches",
            checked = state.enableWidescreenHack,
            onChange = { state.enableWidescreenHack = it },
        )

        Spacer(Modifier.height(24.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onSave, enabled = saveEnabled) { Text("Save") }
            }
        }
    }

    // We keep it outside to avoid rescaning on every open
    var candidates by remember { mutableStateOf<CandidatesState>(CandidatesState.Loading) }

    if (pickerOpen) {
        LaunchedEffect(Unit) {
            candidates = try {
                val result = withContext(Dispatchers.IO) {
                    loadCandidates()
                }

                CandidatesState.Loaded(result)
            } catch (e: Exception) {
                Log.w("MetadataForm", "Failed to load icon candidates", e)
                CandidatesState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }

        IconPickerDialog(
            candidates = candidates,
            selected = state.selectedIcon,
            onPickCandidate = {
                state.selectedIcon = it
                pickerOpen = false
            },
            onPickCustom = {
                pickerOpen = false
                pickCustom.launch("image/*")
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

// Exposed dropdown that assigns one of the LayoutLibrary's layouts to this game for an orientation.
// The selection persists immediately via the caller's onSelect. "(unknown)" shows when the assigned
// id has no matching layout (e.g. a default that is not seeded yet).
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LayoutDropdown(
    label: String,
    selectedId: UUID,
    options: List<GamepadLayout>,
    onSelect: (UUID) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = options.firstOrNull { it.id == selectedId }?.fancyName ?: "(unknown)"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { layout ->
                DropdownMenuItem(
                    text = { Text(layout.fancyName) },
                    onClick = {
                        onSelect(layout.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

// Picks which OS the runner reports to the game through GML's os_type / os_* builtins. Staged and
// committed on Save just like the layout dropdowns. Most games never read os_type, but chapter-aware
// or platform-gated titles can branch on it.
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun OsDropdown(
    selected: GameEntry.RunnerOs,
    onSelect: (GameEntry.RunnerOs) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected.fancyName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Reported OS") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            GameEntry.RunnerOs.entries.forEach { os ->
                DropdownMenuItem(
                    text = { Text(os.fancyName) },
                    onClick = {
                        onSelect(os)
                        expanded = false
                    },
                )
            }
        }
    }
}

// Labeled on/off switch row for a physical-input source (controllers, keyboard). Staged and
// committed on Save like the other fields. Off is an escape hatch for games that misbehave when
// that input is attached (e.g. ones that auto-switch to a console UI when a controller appears).
@Composable
fun InputToggle(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Switch(checked = checked, onCheckedChange = onChange)
    }
}