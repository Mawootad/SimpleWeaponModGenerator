package simpleweaponmodgenerator

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import simpleweaponmodgenerator.YamlPatchConfig.PatchConfigMonoBehavior
import java.io.File

@Serializable
data class PatchEntry(
    @SerialName("Guid") val guid: String,
    @SerialName("Filename") val filename: String,
    @SerialName("PatchType") val patchType: Int = 2
)

@Suppress("PropertyName")
@Serializable
private data class YamlPatchConfig(val MonoBehavior: PatchConfigMonoBehavior) {
    @Serializable
    data class PatchConfigMonoBehavior(
        val m_ObjectHideFlags: Int = 0,
        val m_CorrespondingSourceObject: FileId = FileId(0),
        val m_PrefabInstance: FileId = FileId(0),
        val m_PrefabAsset: FileId = FileId(0),
        val m_GameObject: FileId = FileId(0),
        val m_Enabled: Int = 1,
        val m_EditorHideFlags: Int = 0,
        val m_Script: Script = Script(11500000, "80fe07f61edc4914ac44891e22e1fdf7", 3),
        val m_Name: String,
        val m_EditorClassIdentifier: String = "",
        val Entries: List<PatchEntry>,
    ) {

        @Serializable
        data class FileId(val fileId: Int)

        @Serializable
        data class Script(val fileID: Int, val guid: String, val type: Int)
    }
}

fun patchConfigYaml(patches: List<PatchEntry>, name: String = "genatedPatchesConfig") =
    """
    |%YAML 1.1
    |%TAG !u! tag:unity3d.com,2011:
    |--- !u!114 &11400000
    |${
        Yaml.default.encodeToString(
            YamlPatchConfig.serializer(),
            YamlPatchConfig(
                PatchConfigMonoBehavior(
                    m_Name = name,
                    Entries = patches
                )
            )
        )
    }
    |""".trimMargin()

fun getPatchEntriesFromYaml(file: File) =
    Yaml.default.decodeFromString(YamlPatchConfig.serializer(), file.readText()).MonoBehavior.Entries

@Suppress("PropertyName")
@Serializable
private data class JsonPatchConfig(
    val BundlesLayout: Layout = Layout(emptyList(), emptyList()),
    val BundleDependencies: Dependencies = Dependencies(emptyList()),
    val BlueprintPatches: List<PatchEntry>,
) {
    @Serializable
    data class Layout(val m_Guids: List<String>, val m_Bundles: List<String>)

    @Serializable
    data class Dependencies(val m_List: List<String>)
}

fun patchConfigJson(patches: List<PatchEntry>) = Json.encodeToString(JsonPatchConfig(BlueprintPatches = patches))
