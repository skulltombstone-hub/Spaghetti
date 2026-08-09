package net.perfectdreams.butterscotch.android.library

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.perfectdreams.butterscotch.android.layouts.LayoutLibrary
import net.perfectdreams.butterscotch.android.runtime.EngineRuntimeRegistry
import java.io.File
import java.util.UUID

class GameLibrary private constructor(
    private val rootDir: File,
    private val indexFile: File,
    initial: List<GameEntry>
) {
    val entries: SnapshotStateList<GameEntry> = mutableStateListOf<GameEntry>().apply { addAll(initial) }

    fun bundleDir(entry: GameEntry): File = File(gameDir(entry.id), "bundle")

    fun savesDir(entry: GameEntry): File {
        val activeSlot = entry.saveSlots.first { it.active }
        return slotDir(entry, activeSlot.id)
    }

    fun slotDir(entry: GameEntry, slotId: UUID): File = File(gameDir(entry.id), "saves/$slotId")

    fun logsDir(entry: GameEntry): File = File(gameDir(entry.id), "logs")
    fun logsDir(entryId: UUID): File = File(gameDir(entryId), "logs")

    /**
     * Returns the main executable path for the entry.
     *
     * For current runtimes:
     * - GameMaker uses the imported WAD / data.win-like file
     * - HTML uses the entry point HTML file
     *
     * In the new architecture this method stays small because the runtime
     * itself owns launch behavior, but the library still needs to know the
     * canonical primary file.
     */
    fun mainFile(entry: GameEntry): File {
        return File(
            bundleDir(entry),
            when (val gameType = entry.gameType) {
                is GameEntry.GameType.GameMakerStudio -> gameType.filename
                is GameEntry.GameType.Html -> gameType.entryPoint
            }
        )
    }

    /**
     * Kept for backward compatibility with existing code.
     */
    fun wadPath(entry: GameEntry): File = mainFile(entry)

    fun gameDir(id: UUID): File = File(rootDir, "games/data/$id")

    fun assetsDir(id: UUID): File = File(rootDir, "games/assets/$id")
    fun assetsDir(entry: GameEntry): File = assetsDir(entry.id)

    fun iconFile(id: UUID): File = File(assetsDir(id), "icon.png")
    fun iconFile(entry: GameEntry): File = iconFile(entry.id)

    fun findById(id: UUID): GameEntry? = entries.firstOrNull { it.id == id }

    fun beginStaging(): StagedGame {
        val id = UUID.randomUUID()
        val dir = gameDir(id).apply { mkdirs() }
        File(dir, "bundle").mkdirs()
        File(dir, "saves").mkdirs()
        File(dir, "logs").mkdirs()
        return StagedGame(id, File(dir, "bundle"), File(dir, "saves"))
    }

    fun commit(
        staged: StagedGame,
        title: String,
        gameType: GameEntry.GameType,
        icon: Bitmap? = null,
        portraitLayout: UUID = LayoutLibrary.DEFAULT_PORTRAIT_LAYOUT,
        landscapeLayout: UUID = LayoutLibrary.DEFAULT_LANDSCAPE_LAYOUT,
        runnerOs: GameEntry.RunnerOs = GameEntry.RunnerOs.WINDOWS,
        enablePhysicalControllers: Boolean = true,
        enablePhysicalKeyboard: Boolean = true,
        enableWidescreenHack: Boolean = false,
        postProcessing: GameEntry.PostProcessingSettings = GameEntry.PostProcessingSettings()
    ) {
        val initialSlotId = UUID.randomUUID()
        File(gameDir(staged.id), "saves/$initialSlotId").mkdirs()

        val entry = GameEntry(
            id = staged.id,
            title = title,
            gameType = gameType,
            importedAtMillis = System.currentTimeMillis(),
            favorited = false,
            saveSlots = listOf(
                GameEntry.SaveSlot(
                    id = initialSlotId,
                    active = true,
                    fancyName = "Default",
                )
            ),
            portraitLayout = portraitLayout,
            landscapeLayout = landscapeLayout,
            runnerOs = runnerOs,
            enablePhysicalControllers = enablePhysicalControllers,
            enablePhysicalKeyboard = enablePhysicalKeyboard,
            enableWidescreenHack = enableWidescreenHack,
            postProcessing = postProcessing,
        )

        entries.add(entry)
        syncOrder()

        if (icon != null) {
            runCatching {
                val out = iconFile(staged.id).apply { parentFile?.mkdirs() }
                out.outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }.onFailure { Log.w(TAG, "Failed to write icon for ${staged.id}", it) }
        }

        save()
    }

    fun discardStaging(staged: StagedGame) {
        gameDir(staged.id).deleteRecursively()
    }

    fun update(id: UUID, block: (GameEntry) -> (GameEntry)) {
        val index = entries.indexOfFirst { it.id == id }
        if (index == -1)
            error("Trying to update a entry that doesn't exist! $id")
        entries[index] = block.invoke(entries[index])
        save()
    }

    fun remove(id: UUID) {
        if (entries.removeAll { it.id == id }) {
            gameDir(id).deleteRecursively()
            assetsDir(id).deleteRecursively()
            logsDir(id).deleteRecursively()
            save()
        }
    }

    fun setTitle(id: UUID, title: String) {
        require(title.isNotBlank()) { "Title cannot be blank" }
        update(id) { it.copy(title = title) }
        syncOrder()
        save()
    }

    fun setIcon(id: UUID, bitmap: Bitmap?) {
        val out = iconFile(id)
        if (bitmap == null) {
            out.delete()
        } else {
            out.parentFile?.mkdirs()
            runCatching {
                out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }.onFailure {
                Log.w(TAG, "Failed to write icon for $id", it)
                return
            }
        }
        update(id) { it.copy(iconRevision = it.iconRevision + 1) }
        save()
    }

    fun addSlot(gameId: UUID, name: String): UUID {
        val slotId = UUID.randomUUID()
        update(gameId) { entry ->
            entry.copy(
                saveSlots = entry.saveSlots + GameEntry.SaveSlot(
                    id = slotId,
                    active = false,
                    fancyName = name,
                )
            )
        }
        val entry = findById(gameId) ?: error("Game vanished while adding slot: $gameId")
        slotDir(entry, slotId).mkdirs()
        save()
        return slotId
    }

    fun removeSlot(gameId: UUID, slotId: UUID) {
        val entry = findById(gameId) ?: error("No such game: $gameId")
        require(entry.saveSlots.size > 1) { "Cannot delete the last save slot" }
        val target = entry.saveSlots.first { it.id == slotId }

        val remaining = entry.saveSlots.filter { it.id != slotId }
        val finalSlots = if (target.active) {
            remaining.mapIndexed { i, slot -> slot.copy(active = i == 0) }
        } else {
            remaining
        }

        update(gameId) { it.copy(saveSlots = finalSlots) }
        slotDir(entry, slotId).deleteRecursively()
        save()
    }

    fun copySlot(gameId: UUID, sourceSlotId: UUID, name: String): UUID {
        val entry = findById(gameId) ?: error("No such game: $gameId")
        require(entry.saveSlots.any { it.id == sourceSlotId }) { "Unknown slot $sourceSlotId for game $gameId" }

        val newId = UUID.randomUUID()
        val srcDir = slotDir(entry, sourceSlotId)
        val dstDir = slotDir(entry, newId).apply { mkdirs() }
        if (srcDir.exists()) srcDir.copyRecursively(dstDir, overwrite = true)

        update(gameId) { e ->
            e.copy(
                saveSlots = e.saveSlots + GameEntry.SaveSlot(
                    id = newId,
                    active = false,
                    fancyName = name,
                )
            )
        }

        save()
        return newId
    }

    fun renameSlot(gameId: UUID, slotId: UUID, name: String) {
        update(gameId) { entry ->
            entry.copy(
                saveSlots = entry.saveSlots.map { slot ->
                    if (slot.id == slotId) slot.copy(fancyName = name) else slot
                }
            )
        }
        save()
    }

    fun setActiveSlot(gameId: UUID, slotId: UUID) {
        update(gameId) { entry ->
            require(entry.saveSlots.any { it.id == slotId }) { "Unknown slot $slotId for game $gameId" }
            entry.copy(
                saveSlots = entry.saveSlots.map { slot -> slot.copy(active = slot.id == slotId) }
            )
        }
        save()
    }

    fun save() {
        val payload = json.encodeToString<List<GameEntry>>(entries.toList())
        val tmp = File(indexFile.parentFile, indexFile.name + ".tmp")
        tmp.writeText(payload)
        if (!tmp.renameTo(indexFile)) {
            indexFile.writeText(payload)
            tmp.delete()
        }
    }

    fun syncOrder() {
        entries.sortWith(compareByDescending<GameEntry> { it.favorited }.thenBy { it.title })
    }

    data class StagedGame(val id: UUID, val bundleDir: File, val savesDir: File)

    companion object {
        private const val TAG = "GameLibrary"
        private const val ROOT_DIR_NAME = "butterscotch"
        private const val INDEX_FILE_NAME = "library.json"

        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

        fun load(context: Context): GameLibrary {
            val rootDir = File(context.filesDir, ROOT_DIR_NAME).apply { mkdirs() }
            File(rootDir, "games").mkdirs()
            val indexFile = File(rootDir, INDEX_FILE_NAME)
            val initial = if (indexFile.exists()) parse(indexFile) else emptyList()
            return GameLibrary(rootDir, indexFile, initial).apply {
                this.syncOrder()
            }
        }

        private fun parse(file: File): List<GameEntry> = try {
            json.decodeFromString<List<GameEntry>>(file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse $file; starting with empty library", e)
            emptyList()
        }
    }
}
