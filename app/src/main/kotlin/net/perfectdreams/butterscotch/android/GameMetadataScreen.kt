package net.perfectdreams.butterscotch.android

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import net.perfectdreams.butterscotch.android.components.ButterscotchBackButton
import net.perfectdreams.butterscotch.android.components.ButterscotchTopBar
import net.perfectdreams.butterscotch.android.components.MetadataForm
import net.perfectdreams.butterscotch.android.layouts.GamepadLayout
import net.perfectdreams.butterscotch.android.layouts.LayoutLibrary
import net.perfectdreams.butterscotch.android.library.GameEntry
import net.perfectdreams.butterscotch.android.library.GameLibrary
import net.perfectdreams.butterscotch.android.pe.scanIconCandidates
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GameMetadataScreen(
    gameLibrary: GameLibrary,
    layoutLibrary: LayoutLibrary,
    gameId: UUID,
    nav: NavHostController,
) {
    val entry = gameLibrary.findById(gameId) ?: return

    var title by rememberSaveable { mutableStateOf(entry.title) }

    val originalIcon = remember {
        val f = gameLibrary.iconFile(entry)
        if (f.exists())
            BitmapFactory.decodeFile(f.absolutePath)
        else
            null
    }

    var selectedIcon by remember { mutableStateOf(originalIcon) }

    // Layout selection is staged like title/icon and committed on Save, so changing it enables the
    // Save button instead of persisting silently behind the user's back.
    var portraitLayout by rememberSaveable { mutableStateOf(entry.portraitLayout) }
    var landscapeLayout by rememberSaveable { mutableStateOf(entry.landscapeLayout) }
    var runnerOs by rememberSaveable { mutableStateOf(entry.runnerOs) }
    var enablePhysicalControllers by rememberSaveable { mutableStateOf(entry.enablePhysicalControllers) }

    val titleTrimmed = title.trim()
    val titleChanged = titleTrimmed.isNotBlank() && titleTrimmed != entry.title
    val iconChanged = selectedIcon !== originalIcon
    val layoutsChanged = portraitLayout != entry.portraitLayout || landscapeLayout != entry.landscapeLayout
    val runnerOsChanged = runnerOs != entry.runnerOs
    val controllersChanged = enablePhysicalControllers != entry.enablePhysicalControllers

    Scaffold(
        topBar = {
            ButterscotchTopBar("Metadata", nav, navigationIcon = { ButterscotchBackButton(nav) })
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding).padding(24.dp)) {
            MetadataForm(
                title = title,
                onTitleChange = { title = it },
                selectedIcon = selectedIcon,
                onIconChange = { selectedIcon = it },
                middleContent = {
                    LayoutDropdown(
                        label = "Portrait Layout",
                        selectedId = portraitLayout,
                        options = layoutLibrary.entries.filter { it.orientation == GamepadLayout.GamepadTargetOrientation.PORTRAIT },
                        onSelect = { id -> portraitLayout = id },
                    )
                    Spacer(Modifier.height(16.dp))
                    LayoutDropdown(
                        label = "Landscape Layout",
                        selectedId = landscapeLayout,
                        options = layoutLibrary.entries.filter { it.orientation == GamepadLayout.GamepadTargetOrientation.LANDSCAPE },
                        onSelect = { id -> landscapeLayout = id },
                    )
                    Spacer(Modifier.height(16.dp))
                    OsDropdown(
                        selected = runnerOs,
                        onSelect = { runnerOs = it },
                    )
                    Spacer(Modifier.height(16.dp))
                    ControllerToggle(
                        checked = enablePhysicalControllers,
                        onChange = { enablePhysicalControllers = it },
                    )
                    Spacer(Modifier.height(16.dp))
                },
                loadCandidates = { scanIconCandidates(gameLibrary.bundleDir(entry)) },
                saveEnabled = titleChanged || iconChanged || layoutsChanged || runnerOsChanged || controllersChanged,
                onSave = {
                    if (titleChanged) gameLibrary.setTitle(entry.id, titleTrimmed)
                    if (iconChanged) gameLibrary.setIcon(entry.id, selectedIcon)
                    if (layoutsChanged) gameLibrary.update(entry.id) { it.copy(portraitLayout = portraitLayout, landscapeLayout = landscapeLayout) }
                    if (runnerOsChanged) gameLibrary.update(entry.id) { it.copy(runnerOs = runnerOs) }
                    if (controllersChanged) gameLibrary.update(entry.id) { it.copy(enablePhysicalControllers = enablePhysicalControllers) }
                    nav.popBackStack()
                },
            )
        }
    }
}

// Exposed dropdown that assigns one of the LayoutLibrary's layouts to this game for an orientation.
// The selection persists immediately via the caller's onSelect. "(unknown)" shows when the assigned
// id has no matching layout (e.g. a default that is not seeded yet).
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LayoutDropdown(
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

// Toggles whether physical controllers (Bluetooth/USB gamepads) feed this game's GML gamepad_*
// builtins. Staged and committed on Save like the other fields. Off is an escape hatch for games
// that misbehave when a controller is attached (e.g. ones that auto-switch to a console UI).
@Composable
private fun ControllerToggle(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Enable physical controllers", modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
