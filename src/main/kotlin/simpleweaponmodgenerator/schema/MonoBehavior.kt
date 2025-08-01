package simpleweaponmodgenerator.schema

import com.charleskorn.kaml.AnchorsAndAliases
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File


@Serializable
data class YamlAsset(@SerialName("MonoBehaviour") val monoBehavior: MonoBehavior) {
    @Serializable
    data class MonoBehavior(
        @SerialName("m_ObjectHideFlags") val objectHideFlags: Int,
        @SerialName("m_CorrespondingSourceObject") val correspondingSourceObject: FileId,
        @SerialName("m_PrefabInstance") val prefabInstance: FileId,
        @SerialName("m_PrefabAsset") val prefabAsset: FileId,
        @SerialName("m_GameObject") val gameObject: FileId,
        @SerialName("m_Enabled") val enabled: Int,
        @SerialName("m_EditorHideFlags") val editorHideFlags: Int,
        @SerialName("m_Script") val script: Script,
        @SerialName("m_Name") val name: String,
        @SerialName("m_EditorClassIdentifier") val editorClassIdentifier: String? = null,
        @SerialName("Entries") val entries: List<PatchEntry>? = null,
        @SerialName("Manifest") val manifest: Manifest? = null,
        @SerialName("Settings") val steamConfig: SteamConfig? = null,
    ) {
        @Serializable
        data class FileId(val fileID: Int)

        @Serializable
        data class Script(val fileID: Int, val guid: String, val type: Int)
    }

    companion object {
        private val YAML = Yaml(configuration = YamlConfiguration(anchorsAndAliases = AnchorsAndAliases.Permitted()))
        fun parseMonoBehavior(file: File) = YAML.decodeFromString(serializer(), file.readText()).monoBehavior
    }
}
