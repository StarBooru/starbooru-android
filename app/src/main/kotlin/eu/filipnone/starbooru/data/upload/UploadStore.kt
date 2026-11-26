package eu.filipnone.starbooru.data.upload

import java.io.File
import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The upload list on disk, as JSON in [file], so it's still there after the app closes. */
class UploadStore(private val file: File) {
    /** What was kept, or `null` when the file was there but couldn't be read. */
    fun load(): UploadState? {
        if (!file.exists()) return UploadState()
        return try {
            JSON.decodeFromString(UploadState.serializer(), file.readText())
        } catch (_: IOException) {
            null
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Writes [state] whole, or leaves the last good copy when the disk fails. */
    fun save(state: UploadState) {
        val part = File(file.parentFile, "${file.name}.tmp")
        try {
            file.parentFile?.mkdirs()
            part.writeText(JSON.encodeToString(UploadState.serializer(), state))
            if (!part.renameTo(file)) part.delete()
        } catch (_: IOException) {
            part.delete()
        }
    }

    private companion object {
        val JSON =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
                classDiscriminator = "kind"
            }
    }
}
