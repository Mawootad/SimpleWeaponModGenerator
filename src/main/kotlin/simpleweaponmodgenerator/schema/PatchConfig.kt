package simpleweaponmodgenerator.schema

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import simpleweaponmodgenerator.schema.YamlAsset.MonoBehavior
import simpleweaponmodgenerator.schema.YamlAsset.MonoBehavior.FileId
import simpleweaponmodgenerator.schema.YamlAsset.MonoBehavior.Script

@Serializable
data class PatchEntry(
    @SerialName("Guid") val guid: String,
    @SerialName("Filename") val filename: String,
    @SerialName("PatchType") val patchType: Int,
) {
    constructor(guid: String, filename: String) : this(guid = guid, filename = filename, patchType = 2)
}

fun YamlAsset.Companion.createPatchConfig(entries: List<PatchEntry>, name: String) = YamlAsset(
    MonoBehavior(
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
)

fun patchConfigYaml(patches: List<PatchEntry>, name: String = "genatedPatchesConfig") =
    """
    |%YAML 1.1
    |%TAG !u! tag:unity3d.com,2011:
    |--- !u!114 &11400000
    |${
        Yaml.default.encodeToString(
            YamlAsset.serializer(),
            YamlAsset.createPatchConfig(
                name = name,
                entries = patches
            )
        )
    }
    |""".trimMargin()

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

    fun encode() = MOD_JSON_FORMAT.encodeToString(this)
}

val MOD_JSON_FORMAT by lazy {
    Json {
        prettyPrint = true
        prettyPrintIndent = "    "
    }
}

fun List<PatchEntry>.encodeToJson() = JsonPatchConfig(blueprintPatches = this).encode()
