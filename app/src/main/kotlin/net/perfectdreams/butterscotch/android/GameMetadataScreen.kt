package net.perfectdreams.butterscotch.android

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import net.perfectdreams.butterscotch.android.components.ButterscotchBackButton
import net.perfectdreams.butterscotch.android.components.ButterscotchTopBar
import net.perfectdreams.butterscotch.android.components.MetadataForm
import net.perfectdreams.butterscotch.android.library.GameLibrary
import net.perfectdreams.butterscotch.android.pe.scanIconCandidates

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GameMetadataScreen(
    library: GameLibrary,
    gameId: String,
    nav: NavHostController,
) {
    val entry = library.findById(gameId) ?: return

    var title by rememberSaveable { mutableStateOf(entry.title) }

    val originalIcon = remember {
        val f = library.iconFile(entry)
        if (f.exists())
            BitmapFactory.decodeFile(f.absolutePath)
        else
            null
    }

    var selectedIcon by remember { mutableStateOf(originalIcon) }

    val titleTrimmed = title.trim()
    val titleChanged = titleTrimmed.isNotBlank() && titleTrimmed != entry.title
    val iconChanged = selectedIcon !== originalIcon

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
                loadCandidates = { scanIconCandidates(library.bundleDir(entry)) },
                saveEnabled = titleChanged || iconChanged,
                onSave = {
                    if (titleChanged) library.setTitle(entry.id, titleTrimmed)
                    if (iconChanged) library.setIcon(entry.id, selectedIcon)
                    nav.popBackStack()
                },
            )
        }
    }
}
