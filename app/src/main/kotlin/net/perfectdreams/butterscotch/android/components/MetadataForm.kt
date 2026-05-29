package net.perfectdreams.butterscotch.android.components

import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
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
import net.perfectdreams.butterscotch.android.pe.IconCandidate

/**
 * Shared title-and-icon editor used by both the import flow and the per-game metadata screen.
 *
 * State is hoisted: the caller owns [title]/[selectedIcon] and decides what counts as "saveable"
 * via [saveEnabled]. The form owns transient UI state (picker open, candidate loading, custom-
 * image picker launcher) so callers don't have to duplicate it.
 *
 * [loadCandidates] runs the first time the picker opens; the result is cached for subsequent
 * opens. Callers with already-in-memory candidates (e.g. the import flow) just return them
 * immediately; callers without (the metadata edit screen) do the actual scan inside the lambda.
 * IO dispatch is handled here — the lambda runs on [Dispatchers.IO].
 */
@Composable
fun MetadataForm(
    title: String,
    onTitleChange: (String) -> Unit,
    selectedIcon: Bitmap?,
    onIconChange: (Bitmap?) -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    loadCandidates: suspend () -> List<IconCandidate>,
    modifier: Modifier = Modifier,
    middleContent: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickerOpen by remember { mutableStateOf(false) }

    val pickCustom = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null)
            return@rememberLauncherForActivityResult

        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodeUriToBitmap(context, uri) }
            if (bitmap != null) onIconChange(bitmap)
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            singleLine = true,
            isError = title.isBlank(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))
        middleContent()

        IconSection(
            selected = selectedIcon,
            onClick = {
                pickerOpen = true
            },
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
            selected = selectedIcon,
            onPickCandidate = {
                onIconChange(it)
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
