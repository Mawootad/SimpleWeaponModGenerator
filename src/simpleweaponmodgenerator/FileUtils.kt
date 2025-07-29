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

const val TSV = "tsv"
const val TEXTPROTO = "textproto"
const val NONE_TEXT = "None"

fun textprotoString(weaponList: Collection<Weapon>) =
    """
        |# proto-file: proto/weapon/Weapon.proto
        |# proto-message: weapon.WeaponList
        |
        |${weaponList { weapon += weaponList }}
        |""".trimMargin()

fun tsvString(weaponList: Collection<Weapon>, noneText: String = NONE_TEXT) =
    """
        |${TsvField.entries.joinToString("\t") { it.header }}
        |${weaponList.joinToString("\n") { weapon -> TsvField.entries.joinToString("\t") { weapon.(it.output)(noneText) } }}
        |""".trimMargin()

private enum class TsvField(val header: String, val output: Weapon.(String) -> String) {
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

fun tsvToProto(file: File): List<Weapon> {
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