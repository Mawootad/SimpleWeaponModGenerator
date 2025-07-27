package simpleweaponmodgenerator

import proto.weapon.weaponList
import simpleweaponmodgenerator.parser.WeaponParser
import java.io.File

object Main {
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
                |  --template_path=[../../../]: Root path to the mod template
                |  --weapon_out: File to write parsed weapon textproto data to
                |""".trimMargin()

            )
        }

        val templatePath = argValues["--template_path"] ?: "../../../"

        val parser = WeaponParser(templatePath)
        if ("--weapon_out" in argValues) {
            File(argValues["--weapon_out"]!!).writer().use {
                it.appendLine(
                    """
                    |# proto-file: proto/weapon/Weapon.proto
                    |# proto-message: weapon.WeaponList
                    |""".trimMargin()
                )
                it.appendLine(weaponList { weapon += parser.weaponsWithStrings.values }.toString())
            }
        }
    }
}