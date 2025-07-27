package simpleweaponmodgenerator

import proto.weapon.Weapon
import proto.weapon.Weapon.WeaponAbility
import proto.weapon.Weapon.WeaponAbility.AbilityType
import proto.weapon.weapon
import proto.weapon.weaponList
import simpleweaponmodgenerator.parser.Classifier
import simpleweaponmodgenerator.parser.WeaponParser
import java.io.File

val Weapon.abilities: List<WeaponAbility>
    get() = listOf(
        ability1,
        ability2,
        ability3,
        ability4,
        ability5
    ).filter { it != weapon { } }


object Main {
    private const val TEMPLATE_PATH = "--template_path"
    private const val WEAPON_OUT = "--weapon_out"
    private const val OBTAINABLE = "--obtainable"
    private const val CATEGORIZE_WEAPONS = "--categorize_weapons"
    private const val FORMAT = "--out_format"
    private const val TEXTPROTO = "textproto"
    private const val CSV = "csv"
    private const val NONE_TEXT = "None"

    @JvmStatic
    fun main(args: Array<String>) {
        val argValues = args.associate { it.split("=", limit = 2).let { pair -> pair[0] to pair.getOrNull(1) } }

        if (args.isEmpty() || !args.first().startsWith("--") || "--help" in argValues) {
            println(
                """
                |Usage java -jar SimpleWeaponModGenerator.jar [Options]
                |
                |Utilities for creating Rogue Trader mods that alter weapon stats without needing to use the terrible Unity editor
                |Default parameters assume the utility is placed in a mod folder of the template
                |
                |Options:
                |  $TEMPLATE_PATH=[../../../]: Root path to the mod template
                |  $WEAPON_OUT=FILEPATH: Folder to write parsed weapon textproto data to
                |  $OBTAINABLE: Filters weapon output to obtainable weapons
                |  $CATEGORIZE_WEAPONS: Generates textprotos grouped by inferred typing
                |  $FORMAT=[$TEXTPROTO]|$CSV: Chooses the output format when writing weapon docs
                |""".trimMargin()

            )
        }

        val templatePath = argValues[TEMPLATE_PATH] ?: "../../../"

        val parser = WeaponParser(templatePath)
        if (WEAPON_OUT in argValues) {
            val extension = argValues[FORMAT]?.lowercase() ?: TEXTPROTO
            val generator: (Collection<Weapon>) -> String = when (extension) {
                TEXTPROTO -> this::textprotoString
                CSV -> this::csvString
                else -> error("Unsupported output format $extension")
            }

            val weaponList = if (OBTAINABLE in argValues) {
                parser.weaponsWithStrings.values.filter { it.sourcesList.isNotEmpty() }
            } else {
                parser.weaponsWithStrings.values
            }

            if (CATEGORIZE_WEAPONS in argValues && extension != CSV) {
                for ((type, weapons) in weaponList.groupBy { Classifier.getWeaponType(it) }) {
                    File(argValues[WEAPON_OUT]!! + "/${type}Weapons.$extension").writer()
                        .write(generator(weapons))
                }
            } else {
                File(argValues[WEAPON_OUT]!! + "/Weapons.$extension").writer().write(generator(weaponList))
            }
        }
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
        ATTACK1("Attack 1\tType\tCost", { abilityCsvString(it.ability1) }),
        ATTACK2("Attack 2\tType\tCost", { abilityCsvString(it.ability2) }),
        ATTACK3("Attack 3\tType\tCost", { abilityCsvString(it.ability3) }),
        ATTACK4("Attack 4\tType\tCost", { abilityCsvString(it.ability4) }),
        ATTACK5("Attack 5\tType\tCost", { abilityCsvString(it.ability5) }),
        SOURCES("Sources", { it.sourcesList.joinToString() }),
        EXTRA_FACTS("Extra Facts", { it.extraFactNameList.joinToString("\t") });

        companion object {
            private fun abilityCsvString(ability: WeaponAbility) =
                if (ability.type != AbilityType.ABILITY_NONE) "${ability.abilityBp}\t${ability.type}\t${ability.ap}" else "\t$NONE_TEXT\t"
        }
    }
}