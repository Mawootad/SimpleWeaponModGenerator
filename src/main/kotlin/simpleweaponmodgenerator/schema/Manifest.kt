package simpleweaponmodgenerator.schema

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class Manifest(
    @SerialName("UniqueName") val uniqueName: String,
    @SerialName("Version") val version: String?,
    @SerialName("DisplayName") val displayName: String?,
    @SerialName("Description") val description: String?,
    @SerialName("Author") val author: String?,
    @SerialName("Repository") val repository: String?,
    @SerialName("HomePage") val homePage: String?,
    @SerialName("Dependencies") val dependencies: List<String>,
) {
    fun encodeToJson() = MOD_JSON_FORMAT.encodeToString(
        Manifest(
            uniqueName = uniqueName,
            version = version ?: "",
            displayName = displayName ?: "",
            description = description ?: "",
            author = author ?: "",
            repository = repository ?: "",
            homePage = homePage ?: "",
            dependencies = dependencies
        )
    )

    companion object {
        fun decodeJson(file: File) = MOD_JSON_FORMAT.decodeFromString<Manifest>(file.readText())
    }
}

@Serializable
data class SteamConfig(
    @SerialName("RelativeThumbnailPath") val relativeThumbnailPath: String?,
    @SerialName("WorkshopDescription") val workshopDescription: String?,
    @SerialName("WorkshopId") val workshopId: String?,
)
