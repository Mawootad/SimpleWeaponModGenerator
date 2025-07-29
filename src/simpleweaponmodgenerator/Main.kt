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

    private const val MAKE_TSV = "make_tsv"
    private const val TSV_OUT = "--tsv_path"

    private const val REMOVE_DUPLICATE_INFO = "remove_duplicate_info"
    private const val PATCH_PATH = "--changes_path"

    private const val MAKE_PATCH_DATA = "make_patches"
    private const val SHORT_MOD_PATH = "mod_path"
    private const val MOD_PATH = "--$SHORT_MOD_PATH"

    private const val MAKE_FULL = "make_full"
    private const val MANIFEST = "--manifest"

    private const val TEXTPROTO = "textproto"
    private const val TSV = "tsv"
    const val NONE_TEXT = "None"
    private val ALL_COMMANDS = setOf(REGENERATE_BASELINE, MAKE_TSV, MAKE_PATCH_DATA, REMOVE_DUPLICATE_INFO, MAKE_FULL)

    @JvmStatic
    fun main(args: Array<String>) {
        val argValues = args.associate { it.split("=", limit = 2).let { pair -> pair[0] to pair.getOrNull(1) } }

        if (args.isEmpty() || "--help" in argValues || "-h" in argValues || "help" in argValues || (argValues.keys intersect ALL_COMMANDS).isEmpty()) {
            println(
                """
                |Usage java -jar SimpleWeaponModGenerator.jar [$REGENERATE_BASELINE] [$MAKE_TSV] [$REMOVE_DUPLICATE_INFO] [$MAKE_PATCH_DATA] [$MAKE_FULL] [Options]
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
                |  $PATCH_PATH=[($SHORT_MOD_PATH)/modifications/]: Path containing modified weapon data
                |  
                |  $MAKE_PATCH_DATA: Creates patch files and a patch changes configuration based on textproto or tsv
                |                    files with modified data
                |  
                |  $MAKE_FULL: Creates a full modification zip from a set of patch files, a change manifest, and a mod manifest
                |  $MANIFEST=[($SHORT_MOD_PATH)/manifest.asset]: Path to a .asset or .json manifest file
                |  
                |  --help: Displays this message
                |""".trimMargin()
            )
        }


        val modPath = argValues[MOD_PATH] ?: "./"
        val baselinePath = argValues[BASELINE_PATH] ?: "$modPath/baseline/"
        val templateRoot = argValues[TEMPLATE_PATH] ?: "../../../"
        val parser = WeaponParser(templateRoot)

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
            val toWrite =
                if (OBTAINABLE in argValues) baselineData!!.filter { it.sourcesList.isNotEmpty() } else baselineData!!
            if (SPLIT_BASELINE in argValues) {
                for ((type, weapons) in toWrite.groupBy { Classifier.getWeaponType(it) }) {
                    File("$baselinePath/${type}Weapons.textproto").writer().use {
                        it.append(textprotoString(weapons))
                    }
                }
            } else {
                File("$baselinePath/Weapons.textproto").writer().use { it.append(textprotoString(toWrite)) }
            }

            File("$baselinePath/Guids.txt").writer().use { writer ->
                writer.write(
                    parser.nameToGuidMap.entries.joinToString("\n") { "${it.key}|${it.value}" })
            }
        }

        if (MAKE_TSV in argValues) {
            val tsvPath = argValues[TSV_OUT] ?: "$modPath/weapons.tsv"
            if (baselineData == null) {
                println("Couldn't generate TSV data, couldn't find baseline")
                return
            }

            Files.createDirectories(Path(File(tsvPath).parent))
            File(tsvPath).writeText(tsvString(baselineData))
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
                println("Couldn't remove duplicate patch information, couldn't find baseline")
                return
            }

            for (file in File(patchesPath).listFiles()!!) {
                println("Updating $file")
                val data = when (file.extension) {
                    TSV -> tsvToProto(file)
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

                if (file.extension == TSV) file.writer().use { it.write(tsvString(updatedWeapons, "")) }
                else if (file.extension == TEXTPROTO) file.writer().use { it.write(textprotoString(updatedWeapons)) }
            }
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

    private fun tsvString(weaponList: Collection<Weapon>, noneText: String = NONE_TEXT) =
        """
        |${TsvField.entries.joinToString("\t") { it.header }}
        |${weaponList.joinToString("\n") { weapon -> TsvField.entries.joinToString("\t") { weapon.(it.output)(noneText) } }}
        |""".trimMargin()

    enum class TsvField(val header: String, val output: Weapon.(String) -> String) {
        BLUEPRINT("Blueprint", { blueprintName }),
        NAME("Name", { name }),
        DESCRIPTION("Description", { description }),
        GROUPING("Grouping", { Classifier.getWeaponType(this).toString() }),
        CATEGORY("Category", { if (hasCategory()) category.toString() else it }),
        FAMILY("Family", { if (hasFamily()) family.toString() else it }),
        CLASSIFICATION("Classification", { if (hasClassification()) classification.toString() else noneAsFalse(it) }),
        HEAVY("Heavy", { if (hasHeavy()) heavy.toString() else noneAsFalse(it) }),
        TWO_HANDED("Two Handed", { if (hasTwoHanded()) twoHanded.toString() else noneAsZero(it) }),
        MIN_DAMAGE("Min Damage", { if (hasMinDamage()) minDamage.toString() else noneAsZero(it) }),
        MAX_DAMAGE("Max Damage", { if (hasMaxDamage()) maxDamage.toString() else noneAsZero(it) }),
        ARMOR_PEN("Armor Pen", { if (hasPenetration()) penetration.toString() else noneAsZero(it) }),
        DODGE_REDUCTION("Dodge Reduction", { if (hasDodgeReduction()) dodgeReduction.toString() else noneAsZero(it) }),
        EXTRA_HIT_CHANCE(
            "Extra Hit Chance",
            { if (hasAdditionalHitChance()) additionalHitChance.toString() else noneAsZero(it) }),
        RATE_OF_FIRE("Rate of Fire", { if (hasAdditionalHitChance()) rateOfFire.toString() else noneAsZero(it) }),
        RECOIL("Recoil", { if (hasRecoil()) recoil.toString() else noneAsZero(it) }),
        RANGE("Max Range", { if (hasMaxRange()) maxRange.toString() else noneAsZero(it) }),
        AMMO("Ammo", { if (hasAmmo()) ammo.toString() else noneAsZero(it) }),
        ATTACK1("Attack 1\tType\tCost\tGFX\tOn Hit", { abilityTsvString(ability1, it) }),
        ATTACK2("Attack 2\tType\tCost\tGFX\tOn Hit", { abilityTsvString(ability2, it) }),
        ATTACK3("Attack 3\tType\tCost\tGFX\tOn Hit", { abilityTsvString(ability3, it) }),
        ATTACK4("Attack 4\tType\tCost\tGFX\tOn Hit", { abilityTsvString(ability4, it) }),
        ATTACK5("Attack 5\tType\tCost\tGFX\tOn Hit", { abilityTsvString(ability5, it) }),
        SOURCES("Sources", { sourcesList.joinToString() }),
        EXTRA_FACTS("Extra Facts", { extraFactNameList.joinToString("\t") });

        companion object {
            private fun noneAsZero(noneText: String) = if (noneText == NONE_TEXT) "0" else noneText
            private fun noneAsFalse(noneText: String) = if (noneText == NONE_TEXT) false.toString() else noneText
            private fun abilityTsvString(ability: WeaponAbility, noneText: String) =
                if (ability.type != AbilityType.ABILITY_NONE) "${ability.abilityBpName}\t${ability.type}\t${ability.ap}\t${ability.fxBpName}\t${ability.onHitActionName}" else "\t$noneText\t\t\t"
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

    private fun tsvToProto(file: File): List<Weapon> {
        val lines = file.reader().readLines()

        val processors = buildList<WeaponKt.Dsl.(String) -> Unit> {
            val tsvFields = TsvField.entries.associateBy { it.header.split("\t").first() }
            lines.first().split("\t").forEachIndexed { idx, header ->
                if (this.size > idx) return@forEachIndexed
                this += when (tsvFields[header]) {
                    TsvField.BLUEPRINT -> listOf { blueprintName = it }
                    TsvField.NAME -> listOf { name = it }
                    TsvField.DESCRIPTION -> listOf { description = it }
                    TsvField.GROUPING -> listOf {}
                    TsvField.CATEGORY -> listOf {
                        category = if (it.isEmpty()) WeaponCategory.CATEGORY_NONE else WeaponCategory.valueOf(it)
                    }

                    TsvField.FAMILY -> listOf {
                        family = if (it.isEmpty()) WeaponFamily.FAMILY_NONE else WeaponFamily.valueOf(it)
                    }

                    TsvField.CLASSIFICATION -> listOf {
                        classification =
                            if (it.isEmpty()) {
                                WeaponClassification.CLASSIFICATION_NONE
                            } else {
                                WeaponClassification.valueOf(it)
                            }
                    }

                    TsvField.HEAVY -> listOf { heavy = it.uppercase() == "TRUE" }
                    TsvField.TWO_HANDED -> listOf { twoHanded = it.uppercase() == "TRUE" }
                    TsvField.MIN_DAMAGE -> listOf { minDamage = it.toIntOrNull() ?: 0 }
                    TsvField.MAX_DAMAGE -> listOf { maxDamage = it.toIntOrNull() ?: 0 }
                    TsvField.ARMOR_PEN -> listOf { penetration = it.toIntOrNull() ?: 0 }
                    TsvField.DODGE_REDUCTION -> listOf { dodgeReduction = it.toIntOrNull() ?: 0 }
                    TsvField.EXTRA_HIT_CHANCE -> listOf { additionalHitChance = it.toIntOrNull() ?: 0 }
                    TsvField.RATE_OF_FIRE -> listOf { rateOfFire = it.toIntOrNull() ?: 0 }
                    TsvField.RECOIL -> listOf { recoil = it.toIntOrNull() ?: 0 }
                    TsvField.RANGE -> listOf { maxRange = it.toIntOrNull() ?: 0 }
                    TsvField.AMMO -> listOf { ammo = it.toIntOrNull() ?: 0 }
                    TsvField.ATTACK1 -> weaponAbilityProcessorList({ it.ability1 }) { ability1 }
                    TsvField.ATTACK2 -> weaponAbilityProcessorList({ it.ability2 }) { ability2 }
                    TsvField.ATTACK3 -> weaponAbilityProcessorList({ it.ability3 }) { ability3 }
                    TsvField.ATTACK4 -> weaponAbilityProcessorList({ it.ability4 }) { ability4 }
                    TsvField.ATTACK5 -> weaponAbilityProcessorList({ it.ability5 }) { ability5 }
                    TsvField.SOURCES -> listOf { sources += it.split(", ") }
                    TsvField.EXTRA_FACTS -> List(20) { { if (it.isNotEmpty()) extraFactName += it } }
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