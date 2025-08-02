package simpleweaponmodgenerator

import com.google.protobuf.TextFormat
import proto.weapon.Weapon
import proto.weapon.Weapon.WeaponAbility
import proto.weapon.WeaponList
import proto.weapon.weapon
import java.io.File
import java.nio.file.Files
import kotlin.io.path.Path

val Weapon.abilities: List<WeaponAbility>
    get() = listOf(
        ability1,
        ability2,
        ability3,
        ability4,
        ability5
    ).filter { it != weapon { } }


object Main {
    private const val REGENERATE_BASELINE = "regenerate"
    private const val TEMPLATE_PATH = "--template_path"
    private const val BASELINE_PATH = "--baseline_path"
    private const val SPLIT_BASELINE = "--split_baseline"
    private const val OBTAINABLE = "--obtainable"

    private const val MAKE_TSV = "make_tsv"
    private const val TSV_OUT = "--tsv_path"

    private const val REMOVE_DUPLICATE_INFO = "remove_duplicate_info"
    private const val PATCH_PATH = "--changes_path"

    private const val MAKE_PATCH_DATA = "make_patches"
    private const val SHORT_MOD_PATH = "mod_path"
    private const val MOD_PATH = "--$SHORT_MOD_PATH"

    private const val MAKE_ZIP = "make_zip"
    private const val MANIFEST = "--manifest"
    private const val ZIP_PATH = "--zip_path"

    private val ALL_COMMANDS = setOf(REGENERATE_BASELINE, MAKE_TSV, MAKE_PATCH_DATA, REMOVE_DUPLICATE_INFO, MAKE_ZIP)

    private val ALL_OPTIONS = ALL_COMMANDS + setOf(
        TEMPLATE_PATH,
        BASELINE_PATH,
        SPLIT_BASELINE,
        OBTAINABLE,
        TSV_OUT,
        PATCH_PATH,
        MOD_PATH,
        MANIFEST,
        ZIP_PATH,
    )

    @JvmStatic
    fun main(args: Array<String>) {
        val argValues = args.associate { it.split("=", limit = 2).let { pair -> pair[0] to pair.getOrNull(1) } }

        try {
            run(argValues)
        } catch (e: Exception) {
            println(e.stackTraceToString())
        }

        if (args.isEmpty() || "--help" in argValues || "-h" in argValues || "help" in argValues || (argValues.keys intersect ALL_COMMANDS).isEmpty()) {
            println(
                """
                |Usage java -jar SimpleWeaponModGenerator.jar [$REGENERATE_BASELINE] [$MAKE_TSV] [$REMOVE_DUPLICATE_INFO] [$MAKE_PATCH_DATA] [$MAKE_ZIP] [Options]
                |
                |Utilities for creating Rogue Trader mods that alter weapon stats without needing to use the terrible Unity editor
                |Default parameters assume the utility is placed in a mod folder of the template
                |
                |Commands:
                |  $REGENERATE_BASELINE: Regenerates baseline textproto files containing all information needed to build
                |  $TEMPLATE_PATH=[../../../]: Root path to WhRtModificationTemplate
                |  $MOD_PATH=[./]: Path to mod folder to write data to
                |  $BASELINE_PATH=[($SHORT_MOD_PATH)/baseline/]: Path to write baseline data to or to read baseline data from if not regenerating
                |  $SPLIT_BASELINE: If set splits the baseline by weapon classification
                |  $OBTAINABLE: If set filters baseline to weapons that are included in a known loot pool
                |  
                |  $MAKE_TSV: Creates TSV files based on the baseline textprotos
                |  $TSV_OUT=[($SHORT_MOD_PATH)/weapons.tsv]: Output file to write TSV data to
                |  
                |  $REMOVE_DUPLICATE_INFO: Removes fields already specified in the baseline from change files
                |  $PATCH_PATH=[($SHORT_MOD_PATH)/modifications/]: Path containing modified tsv or textproto weapon data
                |  
                |  $MAKE_PATCH_DATA: Creates patch files and a patch changes configuration based on textproto or tsv
                |                    files with modified data
                |  
                |  $MAKE_ZIP: Creates a full modification zip from a set of patch files, a change manifest, and a mod manifest
                |  $ZIP_PATH=[($SHORT_MOD_PATH)]: Path to write the mod zip file to
                |  
                |  --help: Displays this message
                |""".trimMargin()
            )
        }

        val unrecognized = argValues.keys.filter { it !in ALL_OPTIONS && it.isNotBlank() }
        if (unrecognized.isNotEmpty()) {
            println("Unrecognized arguments $unrecognized")
        }
    }

    private fun run(argValues: Map<String, String?>) {


        val modPath = argValues[MOD_PATH] ?: "./"
        val baselinePath = argValues[BASELINE_PATH] ?: "$modPath/baseline/"
        val templateRoot = argValues[TEMPLATE_PATH] ?: "../../../"
        val parser = WeaponParser(templateRoot, modPath)

        val baselineData = when {
            REGENERATE_BASELINE in argValues -> parser.weaponsWithStrings
            !File(baselinePath).isDirectory -> null
            MAKE_TSV in argValues || REMOVE_DUPLICATE_INFO in argValues || MAKE_PATCH_DATA in argValues -> {
                buildList {
                    for (file in File(baselinePath).listFiles()!!) {
                        if (file.extension != "textproto") continue
                        addAll(TextFormat.parse(file.readText(), WeaponList::class.java).weaponList)
                    }
                }.takeIf { it.isNotEmpty() }
            }

            else -> null
        }

        if (REGENERATE_BASELINE in argValues) {
            Files.createDirectories(Path(baselinePath))
            if (SPLIT_BASELINE in argValues) {
                for ((type, weapons) in baselineData!!.groupBy { Classifier.getWeaponType(it) }) {
                    File("$baselinePath/${type}Weapons.textproto").writer().use {
                        it.append(textprotoString(weapons))
                    }
                }
            } else {
                File("$baselinePath/Weapons.textproto").writer().use { it.append(textprotoString(baselineData!!)) }
            }

            File("$baselinePath/Guids.txt").writer().use { writer ->
                writer.write(
                    parser.nameToGuidMap.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}|${it.value}" })
            }

            if (parser.errors.isNotEmpty()) {
                println("There were some errors:")
                parser.errors.forEach {
                    println(it)
                    println()
                }
            }

            println("Parsing complete, found ${baselineData!!.size} weapons and ${parser.nameToGuidMap.size} blueprints")
        }

        if (MAKE_TSV in argValues) {
            val tsvPath = argValues[TSV_OUT] ?: "$modPath/weapons.tsv"
            if (baselineData == null) {
                println("Couldn't generate TSV data, couldn't find baseline")
                return
            }

            val toWrite =
                if (OBTAINABLE in argValues) baselineData.filter { it.sourcesList.isNotEmpty() } else baselineData

            Files.createDirectories(Path(File(tsvPath).parent))
            File(tsvPath).writeText(tsvString(toWrite))

            println("Created $tsvPath")
        }

        val patchesPath = argValues[PATCH_PATH] ?: "$modPath/modifications"

        val baselineLookupMap by lazy {
            baselineData!!.associateBy { it.blueprintName }
        }

        if (REMOVE_DUPLICATE_INFO in argValues) {
            if (!File(patchesPath).isDirectory) {
                println("Couldn't remove duplicate patch information, ${File(patchesPath).absolutePath} is not a folder")
                return
            }

            if (baselineData == null) {
                println("Couldn't remove duplicate patch information, missing baseline")
                return
            }

            RemoveDuplicateInfo.removeaAllDuplicateInfo(patchesPath) { baselineLookupMap }
            println("Removed duplicate info")
        }

        if (MAKE_PATCH_DATA in argValues) {
            if (!File(patchesPath).isDirectory) {
                println("Couldn't generate patch files, ${File(patchesPath).absolutePath} is not a folder")
                return
            }

            if (baselineData == null) {
                println("Couldn't generate patch files, couldn't find baseline")
                return
            }

            val nameToGuidMap by lazy {
                if (REGENERATE_BASELINE in argValues) {
                    parser.nameToGuidMap
                } else {
                    File("$baselinePath/guids.txt").readLines().filter { it.isNotEmpty() }.associate {
                        val (first, second) = it.split("|")
                        first to second
                    }
                }
            }

            val weapons = File(patchesPath).listFiles()!!.mapNotNull {
                when (it.extension) {
                    TSV -> tsvToProto(it)
                    TEXTPROTO -> TextFormat.parse(it.readText(), WeaponList::class.java).weaponList
                    else -> null
                }
            }.flatten().map { PatchGenerator.getBpsFromNames(it) { nameToGuidMap } }

            PatchGenerator.writePatches(weapons, baselineData, modPath)
        }

        if (MAKE_ZIP in argValues) {
            ZipGenerator.generateZip(modPath, argValues[ZIP_PATH] ?: modPath)
        }
    }
}