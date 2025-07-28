package simpleweaponmodgenerator

import com.google.protobuf.TextFormat
import proto.weapon.Weapon
import proto.weapon.Weapon.WeaponAbility
import proto.weapon.Weapon.WeaponAbility.AbilityType
import proto.weapon.Weapon.WeaponCategory
import proto.weapon.Weapon.WeaponClassification
import proto.weapon.Weapon.WeaponFamily
import proto.weapon.WeaponKt
import proto.weapon.WeaponKt.weaponAbility
import proto.weapon.WeaponList
import proto.weapon.copy
import proto.weapon.weapon
import proto.weapon.weaponList
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

    private const val MAKE_CSV = "make_csv"
    private const val CSV_OUT = "--csv_path"

    private const val REMOVE_DUPLICATE_INFO = "remove_duplicate_info"
    private const val PATCH_PATH = "--changes_path"

    private const val MAKE_PATCH_DATA = "make_patches"
    private const val MOD_PATH = "--mod_path"

    private const val MAKE_FULL = "make_full"
    private const val MANIFEST = "--manifest"

    private const val TEXTPROTO = "textproto"
    private const val CSV = "csv"
    const val NONE_TEXT = "None"
    private val ALL_COMMANDS = setOf(REGENERATE_BASELINE, MAKE_CSV, MAKE_PATCH_DATA, REMOVE_DUPLICATE_INFO, MAKE_FULL)

    @JvmStatic
    fun main(args: Array<String>) {
        val argValues = args.associate { it.split("=", limit = 2).let { pair -> pair[0] to pair.getOrNull(1) } }

        if (args.isEmpty() || "--help" in argValues || "-h" in argValues || "help" in argValues || (argValues.keys intersect ALL_COMMANDS).isEmpty()) {
            println(
                """
                |Usage java -jar SimpleWeaponModGenerator.jar [$REGENERATE_BASELINE] [$MAKE_CSV] [$REMOVE_DUPLICATE_INFO] [$MAKE_PATCH_DATA] [$MAKE_FULL] [Options]
                |
                |Utilities for creating Rogue Trader mods that alter weapon stats without needing to use the terrible Unity editor
                |Default parameters assume the utility is placed in a mod folder of the template
                |
                |Commands:
                |  $REGENERATE_BASELINE: Regenerates baseline textproto files containing all information needed to build
                |  $TEMPLATE_PATH=[../../../]: Root path to WhRtModificationTemplate
                |  $BASELINE_PATH=[baseline/]: Path to write baseline data to or to read baseline data from if not regenerating
                |  $SPLIT_BASELINE: If set splits the baseline by weapon classification
                |  $OBTAINABLE: If set filters baseline to weapons that are included in a known loot pool
                |  
                |  $MAKE_CSV: Creates CSV files based on the baseline textprotos
                |  $CSV_OUT=[./weapons.csv]: Output file to write CSV data to
                |  
                |  $REMOVE_DUPLICATE_INFO: Removes fields already specified in the baseline from change files
                |  $PATCH_PATH=[modifications/]: Path containing modified weapon data
                |  
                |  $MAKE_PATCH_DATA: Creates patch files and a patch changes configuration based on textproto or csv
                |                    files with modified data
                |  $MOD_PATH=[./]: Path to mod folder to write data to
                |  
                |  $MAKE_FULL: Creates a full modification zip from a set of patch files, a change manifest, and a mod manifest
                |  $MANIFEST=[./manifest.json]: Path to a .asset or .json manifest file
                |  
                |  --help: Displays this message
                |""".trimMargin()
            )
        }

        val baselinePath = argValues[BASELINE_PATH] ?: "./baseline/"
        val templateRoot = argValues[TEMPLATE_PATH] ?: "../../../"
        val parser = WeaponParser(templateRoot)

        val baselineData = when {
            REGENERATE_BASELINE in argValues -> parser.weaponsWithStrings
            !File(baselinePath).isDirectory -> null
            MAKE_CSV in argValues || REMOVE_DUPLICATE_INFO in argValues || MAKE_PATCH_DATA in argValues -> {
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
        }

        if (MAKE_CSV in argValues) {
            val csvPath = argValues[CSV_OUT] ?: "./weapons.csv"
            if (baselineData == null) {
                println("Couldn't generate CSV data, couldn't find baseline")
                return
            }

            Files.createDirectories(Path(File(csvPath).parent))
            File(csvPath).writeText(csvString(baselineData))
        }

        val modificationsPath = argValues[MOD_PATH] ?: "./modifications"

        val baselineLookupMap by lazy {
            baselineData!!.associateBy { it.blueprintName }
        }

        if (REMOVE_DUPLICATE_INFO in argValues) {
            if (!File(modificationsPath).isDirectory) {
                println("Couldn't remove duplicate patch information, ${File(modificationsPath).absolutePath} is not a folder")
                return
            }

            if (baselineData == null) {
                println("Couldn't remove duplicate patch information, couldn't find baseline")
                return
            }

            for (file in File(modificationsPath).listFiles()!!) {
                val data = when (file.extension) {
                    CSV -> csvToProto(file)
                    TEXTPROTO -> TextFormat.parse(file.readText(), WeaponList::class.java).weaponList
                    else -> continue
                }

                val updatedWeapons = data.mapNotNull {
                    val baseline = baselineLookupMap[it.blueprintName]
                    if (baseline == null) {
                        println("Couldn't remove duplicate data, no baseline: $it")
                        return@mapNotNull it
                    }
                    removeDuplicateInfo(it, baseline)
                }

                if (file.extension == CSV) file.writer().use { it.write(csvString(updatedWeapons)) }
                else if (file.extension == TEXTPROTO) file.writer().use { it.write(textprotoString(updatedWeapons)) }
            }
        }

        val modPath = argValues[MOD_PATH] ?: "./"

        if (MAKE_PATCH_DATA in argValues) {
            if (!File(modificationsPath).isDirectory) {
                println("Couldn't generate patch files, ${File(modificationsPath).absolutePath} is not a folder")
                return
            }

            if (baselineData == null) {
                println("Couldn't generate patch files, couldn't find baseline")
                return
            }

            val weapons = File(modificationsPath).listFiles()!!.mapNotNull {
                when (it.extension) {
                    CSV -> csvToProto(it)
                    TEXTPROTO -> TextFormat.parse(it.readText(), WeaponList::class.java).weaponList
                    else -> null
                }
            }.flatten().map { parser.getBpsFromNames(it) }

            PatchGenerator.writePatches(weapons, baselineData, modPath)
        }
    }

    private fun removeDuplicateInfo(weapon: Weapon, baseline: Weapon): Weapon? =
        weapon.copy {
            // This really needs to use descriptor reflection
            if (category == baseline.category) clearCategory()
            if (family == baseline.family) clearFamily()
            if (classification == baseline.classification) clearClassification()
            if (heavy == baseline.heavy) clearHeavy()
            if (twoHanded == baseline.twoHanded) clearTwoHanded()
            if (minDamage == baseline.minDamage) clearMinDamage()
            if (maxDamage == baseline.maxDamage) clearMaxDamage()
            if (penetration == baseline.penetration) clearPenetration()
            if (dodgeReduction == baseline.dodgeReduction) clearDodgeReduction()
            if (additionalHitChance == baseline.additionalHitChance) clearAdditionalHitChance()
            if (recoil == baseline.recoil) clearRecoil()
            if (maxRange == baseline.maxRange) clearMaxRange()
            if (ammo == baseline.ammo) clearAmmo()
            if (rateOfFire == baseline.rateOfFire) clearRateOfFire()
            fun removeDuplicateAbility(updated: WeaponAbility, baselineAbility: WeaponAbility) =
                updated.copy {
                    if (abilityBpName == baselineAbility.abilityBpName) clearAbilityBpName()
                    if (abilityBp == baselineAbility.abilityBp) clearAbilityBp()
                    if (ap == baselineAbility.ap) clearAp()
                    if (type == baselineAbility.type) clearType()
                    if (fxBp == baselineAbility.fxBp) clearFxBp()
                    if (fxBpName == baselineAbility.fxBpName) clearFxBpName()
                    if (onHitActions == baselineAbility.onHitActions) clearOnHitActions()
                    if (onHitActionName == baselineAbility.onHitActionName) clearOnHitActionName()
                }

            if (hasAbility1()) {
                ability1 = removeDuplicateAbility(ability1, baseline.ability1)
                if (ability1 == weaponAbility {}) clearAbility1()
            }
            if (hasAbility2()) {
                ability2 = removeDuplicateAbility(ability2, baseline.ability2)
                if (ability2 == weaponAbility {}) clearAbility2()
            }
            if (hasAbility3()) {
                ability3 = removeDuplicateAbility(ability3, baseline.ability3)
                if (ability3 == weaponAbility {}) clearAbility3()
            }
            if (hasAbility4()) {
                ability4 = removeDuplicateAbility(ability4, baseline.ability4)
                if (ability4 == weaponAbility {}) clearAbility4()
            }
            if (hasAbility5()) {
                ability5 = removeDuplicateAbility(ability5, baseline.ability5)
                if (ability5 == weaponAbility {}) clearAbility5()
            }

            val extraFacts = extraFact subtract baseline.extraFactList
            extraFact.clear()
            extraFact += extraFacts
            val extraFactNames = extraFactName subtract baseline.extraFactNameList
            extraFactName.clear()
            extraFactName += extraFactNames
        }.takeIf {
            it.hasCategory()
                    || it.hasFamily()
                    || it.hasClassification()
                    || it.hasHeavy()
                    || it.hasTwoHanded()
                    || it.hasMinDamage()
                    || it.hasMaxDamage()
                    || it.hasPenetration()
                    || it.hasDodgeReduction()
                    || it.hasAdditionalHitChance()
                    || it.hasRecoil()
                    || it.hasMaxRange()
                    || it.hasAmmo()
                    || it.hasRateOfFire()
                    || it.hasAbility1()
                    || it.hasAbility2()
                    || it.hasAbility3()
                    || it.hasAbility4()
                    || it.hasAbility5()
                    || it.extraFactList.isNotEmpty()
        }

    private fun textprotoString(weaponList: Collection<Weapon>) =
        """
        |# proto-file: proto/weapon/Weapon.proto
        |# proto-message: weapon.WeaponList
        |
        |${weaponList { weapon += weaponList }}
        |""".trimMargin()

    private fun csvString(weaponList: Collection<Weapon>) =
        """
        |${CsvField.entries.joinToString("\t") { it.header }}
        |${weaponList.joinToString("\n") { weapon -> CsvField.entries.joinToString("\t") { it.output(weapon) } }}
        |""".trimMargin()

    enum class CsvField(val header: String, val output: (Weapon) -> String) {
        BLUEPRINT("Blueprint", { it.blueprintName }),
        NAME("Name", { it.name }),
        DESCRIPTION("Description", { it.description }),
        GROUPING("Grouping", { Classifier.getWeaponType(it).toString() }),
        CATEGORY("Category", { if (it.hasCategory()) it.category.toString() else NONE_TEXT }),
        FAMILY("Family", { if (it.hasFamily()) it.family.toString() else NONE_TEXT }),
        CLASSIFICATION("Classification", { if (it.hasClassification()) it.classification.toString() else NONE_TEXT }),
        HEAVY("Heavy", { it.heavy.toString() }),
        TWO_HANDED("Two Handed", { it.twoHanded.toString() }),
        MIN_DAMAGE("Min Damage", { it.minDamage.toString() }),
        MAX_DAMAGE("Max Damage", { it.maxDamage.toString() }),
        ARMOR_PEN("Armor Pen", { it.penetration.toString() }),
        DODGE_REDUCTION("Dodge Reduction", { it.dodgeReduction.toString() }),
        EXTRA_HIT_CHANCE("Extra Hit Chance", { it.additionalHitChance.toString() }),
        RATE_OF_FIRE("Rate of Fire", { it.rateOfFire.toString() }),
        RECOIL("Recoil", { it.recoil.toString() }),
        RANGE("Max Range", { it.maxRange.toString() }),
        AMMO("Ammo", { it.ammo.toString() }),
        ATTACK1("Attack 1\tType\tCost\tGFX\tOn Hit", { abilityCsvString(it.ability1) }),
        ATTACK2("Attack 2\tType\tCost\tGFX\tOn Hit", { abilityCsvString(it.ability2) }),
        ATTACK3("Attack 3\tType\tCost\tGFX\tOn Hit", { abilityCsvString(it.ability3) }),
        ATTACK4("Attack 4\tType\tCost\tGFX\tOn Hit", { abilityCsvString(it.ability4) }),
        ATTACK5("Attack 5\tType\tCost\tGFX\tOn Hit", { abilityCsvString(it.ability5) }),
        SOURCES("Sources", { it.sourcesList.joinToString() }),
        EXTRA_FACTS("Extra Facts", { it.extraFactNameList.joinToString("\t") });

        companion object {
            private fun abilityCsvString(ability: WeaponAbility) =
                if (ability.type != AbilityType.ABILITY_NONE) "${ability.abilityBpName}\t${ability.type}\t${ability.ap}\t${ability.fxBpName}\t${ability.onHitActionName}" else "\t$NONE_TEXT\t"
        }
    }

    private fun weaponAbilityProcessorList(
        ability: (WeaponKt.Dsl) -> WeaponAbility,
        assign: WeaponKt.Dsl.(WeaponAbility) -> Unit
    ): List<WeaponKt.Dsl.(String) -> Unit> =
        listOf(
            { assign(ability(this).copy { abilityBpName = it }) },
            {
                assign(ability(this).copy {
                    type = if (it.isEmpty()) {
                        AbilityType.ABILITY_NONE
                    } else {
                        AbilityType.valueOf(it)
                    }
                })
            },
            { assign(ability(this).copy { ap = it.toIntOrNull() ?: 0 }) },
            { assign(ability(this).copy { fxBpName = it }) },
            { assign(ability(this).copy { onHitActionName = it }) },
        )

    private fun csvToProto(file: File): List<Weapon> {
        val lines = file.reader().readLines()

        val processors = buildList<WeaponKt.Dsl.(String) -> Unit> {
            val csvFields = CsvField.entries.associateBy { it.header.split("\t").first() }
            lines.first().split("\t").forEachIndexed { idx, header ->
                if (this.size > idx) return@forEachIndexed
                this += when (csvFields[header]) {
                    CsvField.BLUEPRINT -> listOf { blueprintName = it }
                    CsvField.NAME -> listOf { name = it }
                    CsvField.DESCRIPTION -> listOf { description = it }
                    CsvField.GROUPING -> listOf {}
                    CsvField.CATEGORY -> listOf {
                        category = if (it.isEmpty()) WeaponCategory.CATEGORY_NONE else WeaponCategory.valueOf(it)
                    }

                    CsvField.FAMILY -> listOf {
                        family = if (it.isEmpty()) WeaponFamily.FAMILY_NONE else WeaponFamily.valueOf(it)
                    }

                    CsvField.CLASSIFICATION -> listOf {
                        classification =
                            if (it.isEmpty()) {
                                WeaponClassification.CLASSIFICATION_NONE
                            } else {
                                WeaponClassification.valueOf(it)
                            }
                    }

                    CsvField.HEAVY -> listOf { heavy = it.uppercase() == "TRUE" }
                    CsvField.TWO_HANDED -> listOf { twoHanded = it.uppercase() == "TRUE" }
                    CsvField.MIN_DAMAGE -> listOf { minDamage = it.toIntOrNull() ?: 0 }
                    CsvField.MAX_DAMAGE -> listOf { maxDamage = it.toIntOrNull() ?: 0 }
                    CsvField.ARMOR_PEN -> listOf { penetration = it.toIntOrNull() ?: 0 }
                    CsvField.DODGE_REDUCTION -> listOf { dodgeReduction = it.toIntOrNull() ?: 0 }
                    CsvField.EXTRA_HIT_CHANCE -> listOf { additionalHitChance = it.toIntOrNull() ?: 0 }
                    CsvField.RATE_OF_FIRE -> listOf { rateOfFire = it.toIntOrNull() ?: 0 }
                    CsvField.RECOIL -> listOf { recoil = it.toIntOrNull() ?: 0 }
                    CsvField.RANGE -> listOf { maxRange = it.toIntOrNull() ?: 0 }
                    CsvField.AMMO -> listOf { ammo = it.toIntOrNull() ?: 0 }
                    CsvField.ATTACK1 -> weaponAbilityProcessorList({ it.ability1 }) { ability1 }
                    CsvField.ATTACK2 -> weaponAbilityProcessorList({ it.ability2 }) { ability2 }
                    CsvField.ATTACK3 -> weaponAbilityProcessorList({ it.ability3 }) { ability3 }
                    CsvField.ATTACK4 -> weaponAbilityProcessorList({ it.ability4 }) { ability4 }
                    CsvField.ATTACK5 -> weaponAbilityProcessorList({ it.ability5 }) { ability5 }
                    CsvField.SOURCES -> listOf { sources += it.split(", ") }
                    CsvField.EXTRA_FACTS -> List(20) { { if (it.isNotEmpty()) extraFactName += it } }
                    null -> {
                        listOf {}
                    }
                }
            }
        }

        return lines.drop(1).map { line ->
            weapon {
                line.split("\t").zip(processors).forEach { (token, processor) ->
                    val string = token.trim()
                    if (string == NONE_TEXT) processor("")
                    else if (string.isNotEmpty()) processor(string)
                }
            }
        }

    }
}