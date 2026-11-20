package app.shining.server.watch

import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Keeps [WatchState] in one JSON file. Saving writes a temporary file and then moves it over the
 * old one, so a crash mid-write can't leave half a file behind.
 */
class StateStore(
    private val file: File,
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true },
) {
    fun load(): WatchState =
        if (file.exists()) json.decodeFromString(WatchState.serializer(), file.readText()) else WatchState()

    fun save(state: WatchState) {
        file.absoluteFile.parentFile.mkdirs()
        val temp = File(file.absoluteFile.parentFile, "${file.name}.tmp")
        temp.writeText(json.encodeToString(WatchState.serializer(), state))
        Files.move(
            temp.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }
}
