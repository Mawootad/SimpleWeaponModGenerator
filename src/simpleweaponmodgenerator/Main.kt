package simpleweaponmodgenerator

import proto.weapon.Weapon
import proto.weapon.Weapon.WeaponAbility
import proto.weapon.Weapon.WeaponAbility.AbilityType
import proto.weapon.Weapon.WeaponCategory
import proto.weapon.Weapon.WeaponClassification
import proto.weapon.Weapon.WeaponFamily
import proto.weapon.WeaponKt
import proto.weapon.copy
import proto.weapon.weapon
import proto.weapon.weaponList
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
    private const val INPUT_PATH = "--input_path"
    private const val MOD_PATH = "--mod_path"
    private const val SOURCE_TEXTPROTOS = "--source_textprotos"
    private const val CSV = "csv"
    const val NONE_TEXT = "None"

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
                |  
                |  $SOURCE_TEXTPROTOS=FILEPATH: Folder containing textproto data to read from instead of reading directly from a mod template
                |  
                |  $MOD_PATH=FILEPATH: Folder to write mod data to
                |  $INPUT_PATH=FILEPATH: Folder to read modified weapon data from
                |  
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
                parser.weaponsWithStrings.filter { it.sourcesList.isNotEmpty() }
            } else {
                parser.weaponsWithStrings
            }

            if (CATEGORIZE_WEAPONS in argValues && extension != CSV) {
                for ((type, weapons) in weaponList.groupBy { Classifier.getWeaponType(it) }) {
                    File(argValues[WEAPON_OUT]!! + "/${type}Weapons.$extension").writer().use {
                        it.append(generator(weapons))
                    }
                }
            } else {
                File(argValues[WEAPON_OUT]!! + "/Weapons.$extension").writer().use { it.append(generator(weaponList)) }
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
                if (ability.type != AbilityType.ABILITY_NONE) "${ability.abilityBpName}\t${ability.type}\t${ability.ap}" else "\t$NONE_TEXT\t"
        }
    }

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
                        category = if (it == NONE_TEXT) WeaponCategory.CATEGORY_NONE else WeaponCategory.valueOf(it)
                    }

                    CsvField.FAMILY -> listOf {
                        family = if (it == NONE_TEXT) WeaponFamily.FAMILY_NONE else WeaponFamily.valueOf(it)
                    }

                    CsvField.CLASSIFICATION -> listOf {
                        classification =
                            if (it == NONE_TEXT) {
                                WeaponClassification.CLASSIFICATION_NONE
                            } else {
                                WeaponClassification.valueOf(it)
                            }
                    }

                    CsvField.HEAVY -> listOf { heavy = it.uppercase() == "TRUE" }
                    CsvField.TWO_HANDED -> listOf { twoHanded = it.uppercase() == "TRUE" }
                    CsvField.MIN_DAMAGE -> listOf { minDamage = it.toInt() }
                    CsvField.MAX_DAMAGE -> listOf { maxDamage = it.toInt() }
                    CsvField.ARMOR_PEN -> listOf { penetration = it.toInt() }
                    CsvField.DODGE_REDUCTION -> listOf { dodgeReduction = it.toInt() }
                    CsvField.EXTRA_HIT_CHANCE -> listOf { additionalHitChance = it.toInt() }
                    CsvField.RATE_OF_FIRE -> listOf { rateOfFire = it.toInt() }
                    CsvField.RECOIL -> listOf { recoil = it.toInt() }
                    CsvField.RANGE -> listOf { maxRange = it.toInt() }
                    CsvField.AMMO -> listOf { ammo = it.toInt() }
                    CsvField.ATTACK1 -> listOf({ ability1 = ability1.copy { abilityBpName = it } }, {
                        ability1 = ability1.copy {
                            type = if (it == NONE_TEXT) {
                                AbilityType.ABILITY_NONE
                            } else {
                                AbilityType.valueOf(it)
                            }
                        }
                    }, { ability1 = ability1.copy { ap = it.toInt() } })

                    CsvField.ATTACK2 -> listOf({ ability2 = ability2.copy { abilityBpName = it } }, {
                        ability2 = ability2.copy {
                            type = if (it == NONE_TEXT) {
                                AbilityType.ABILITY_NONE
                            } else {
                                AbilityType.valueOf(it)
                            }
                        }
                    }, { ability2 = ability2.copy { ap = it.toInt() } })

                    CsvField.ATTACK3 -> listOf({ ability3 = ability3.copy { abilityBpName = it } }, {
                        ability3 = ability3.copy {
                            type = if (it == NONE_TEXT) {
                                AbilityType.ABILITY_NONE
                            } else {
                                AbilityType.valueOf(it)
                            }
                        }
                    }, { ability3 = ability3.copy { ap = it.toInt() } })

                    CsvField.ATTACK4 -> listOf({ ability4 = ability4.copy { abilityBpName = it } }, {
                        ability4 = ability4.copy {
                            type = if (it == NONE_TEXT) {
                                AbilityType.ABILITY_NONE
                            } else {
                                AbilityType.valueOf(it)
                            }
                        }
                    }, { ability4 = ability4.copy { ap = it.toInt() } })

                    CsvField.ATTACK5 -> listOf({ ability5 = ability5.copy { abilityBpName = it } }, {
                        ability5 = ability5.copy {
                            type = if (it == NONE_TEXT) {
                                AbilityType.ABILITY_NONE
                            } else {
                                AbilityType.valueOf(it)
                            }
                        }
                    }, { ability5 = ability5.copy { ap = it.toInt() } })

                    CsvField.SOURCES -> listOf { sources += it.split(", ") }
                    CsvField.EXTRA_FACTS -> List(20) { { extraFactName += it } }
                    null -> {
                        listOf {}
                    }
                }
            }
        }

        return lines.drop(1).map { line ->
            weapon {
                line.split("\t").zip(processors).forEach { (token, processor) ->
                    if (token.isNotBlank()) processor(token)
                }
            }
        }

    }
}