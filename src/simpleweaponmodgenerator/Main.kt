package simpleweaponmodgenerator

import proto.weapon.Weapon
import proto.weapon.Weapon.WeaponAbility
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
    private val TEXTPROTO_HEADER =
        """
        |# proto-file: proto/weapon/Weapon.proto
        |# proto-message: weapon.WeaponList
        |""".trimMargin()

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
                |""".trimMargin()

            )
        }

        val templatePath = argValues[TEMPLATE_PATH] ?: "../../../"

        val parser = WeaponParser(templatePath)
        if (WEAPON_OUT in argValues) {
            val weaponList = if (OBTAINABLE in argValues) {
                parser.weaponsWithStrings.values.filter { it.sourcesList.isNotEmpty() }
            } else {
                parser.weaponsWithStrings.values
            }

            if (CATEGORIZE_WEAPONS in argValues) {
                for ((type, weapons) in weaponList.groupBy { Classifier.getWeaponType(it) }) {
                    File(argValues[WEAPON_OUT]!! + "/${type}Weapons.textproto").writer().use {
                        it.appendLine(TEXTPROTO_HEADER)
                        it.appendLine(weaponList { weapon += weapons }.toString())
                    }
                }
            } else {
                File(argValues[WEAPON_OUT]!! + "/Weapons.textproto").writer().use {
                    it.appendLine(TEXTPROTO_HEADER)
                    it.appendLine(weaponList { weapon += weaponList }.toString())
                }
            }
        }
    }
}