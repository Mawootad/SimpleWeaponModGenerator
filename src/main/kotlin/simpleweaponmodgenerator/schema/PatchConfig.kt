package simpleweaponmodgenerator.schema

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import simpleweaponmodgenerator.schema.YamlPatchConfig.PatchConfigMonoBehavior
import java.io.File

@Serializable
data class PatchEntry(
    @SerialName("Guid") val guid: String,
    @SerialName("Filename") val filename: String,
    @SerialName("PatchType") val patchType: Int,
) {
    constructor(guid: String, filename: String) : this(guid = guid, filename = filename, patchType = 2)
}

@Serializable
private data class YamlPatchConfig(@SerialName("MonoBehavior") val monoBehavior: PatchConfigMonoBehavior) {
    @Serializable
    data class PatchConfigMonoBehavior(
        @SerialName("m_ObjectHideFlags") val objectHideFlags: Int,
        @SerialName("m_CorrespondingSourceObject") val correspondingSourceObject: FileId,
        @SerialName("m_PrefabInstance") val prefabInstance: FileId,
        @SerialName("m_PrefabAsset") val prefabAsset: FileId,
        @SerialName("m_GameObject") val gameObject: FileId,
        @SerialName("m_Enabled") val enabled: Int,
        @SerialName("m_EditorHideFlags") val editorHideFlags: Int,
        @SerialName("m_Script") val script: Script,
        @SerialName("m_Name") val name: String,
        @SerialName("m_EditorClassIdentifier") val editorClassIdentifier: String,
        @SerialName("Entries") val entries: List<PatchEntry>,
    ) {
        constructor(entries: List<PatchEntry>, name: String) : this(
            objectHideFlags = 0,
            correspondingSourceObject = FileId(0),
            prefabInstance = FileId(0),
            prefabAsset = FileId(0),
            gameObject = FileId(0),
            enabled = 1,
            editorHideFlags = 0,
            script = Script(11500000, "80fe07f61edc4914ac44891e22e1fdf7", 3),
            name = name,
            editorClassIdentifier = "",
            entries = entries,
        )

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
                    name = name,
                    entries = patches
                )
            )
        )
    }
    |""".trimMargin()

fun getPatchEntriesFromYaml(file: File) =
    Yaml.default.decodeFromString(YamlPatchConfig.serializer(), file.readText()).monoBehavior.entries

@Serializable
private data class JsonPatchConfig(
    @SerialName("BundlesLayout") val bundlesLayout: Layout,
    @SerialName("BundleDependencies") val bundleDependencies: Dependencies,
    @SerialName("BlueprintPatches") val blueprintPatches: List<PatchEntry>,
) {
    constructor(blueprintPatches: List<PatchEntry>) : this(
        blueprintPatches = blueprintPatches,
        bundlesLayout = Layout(emptyList(), emptyList()),
        bundleDependencies = Dependencies(emptyList())
    )

    @Serializable
    data class Layout(
        @SerialName("m_Guids") val guids: List<String>,
        @SerialName("m_Bundles") val bundles: List<String>,
    )

    @Serializable
    data class Dependencies(@SerialName("m_List") val list: List<String>)
}

fun patchConfigJson(patches: List<PatchEntry>) = Json.encodeToString(JsonPatchConfig(blueprintPatches = patches))
