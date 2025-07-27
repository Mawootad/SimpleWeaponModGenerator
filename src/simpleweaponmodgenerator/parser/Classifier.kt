package simpleweaponmodgenerator.parser

import proto.weapon.Weapon
import proto.weapon.Weapon.WeaponCategory
import proto.weapon.Weapon.WeaponClassification
import proto.weapon.Weapon.WeaponFamily
import simpleweaponmodgenerator.abilities
import simpleweaponmodgenerator.parser.Classifier.WeaponTypes.*

private const val SNIPER_RIFLE_CRIT_FEATURE = "7712185d9ea049609fda2784a6ca75ce"
private const val AXE_BLEED_ABILITY = "a270dbb75ea44128b951b5d18d0b6bed"
private const val NAVIGATOR_PROFESSION_FEATURE = "8e0cfa654ec24dbbba9e80c27433cc8e"
private val WILLPOWER_BOOST_TALENTS = setOf(
    "a5038c9866404df58f22b175262a2989",
    "98a981e836d7416bbd498d09797b78ce",
    "b6444939f07e458886eb4f5eb62667e7",
    "93cc723519914813b176a0d0a410d81e",
)
private const val CHAIN_LIGHTNING_ABILITY = "7b68b4aa3c024f348a20dce3ef172e40"
private const val WANDERER_PORTENT_PRECISE_SHOT = "7315f74b9a084b1d804caa90e9a26f47"
private const val POWER_PICK_ABILITY = "08c7e7f3ee8e4090b1f4f75023bbcf30"
private const val WEBBER_ABILITY = "52d98e3eda934168b6c60e8670546a1d"
private const val AELDARI_PROFICIENCY = "365ad1a4ef1b4a47be74509c33b2be3b"
private const val DRUKHARI_PROFICIENCY = "b5fe044e47604f47bf99ebd14440b579"
private const val PRECISE_SHOT = "8fe7633db25d46a8bebc2868b8acff12"
private val DARKLIGHT_BLAST =
    setOf("a39e837b419b4b49a06951650718d074", "8a31e565093b4487bbb2679a964296d4", "b2aae11f1461415dbc4bf59b50ed5dec")

object Classifier {
    enum class WeaponTypes {
        Other,
        Arc,
        Webber,
        Pistol,
        Flame,
        BlastGun,
        Bolter,
        Autogun,
        Lasgun,
        Rifle,
        Plasma,
        Melta,
        Shotgun,
        Sword,
        ForceSword,
        RockSaw,
        Hammer,
        Axe,
        PowerPick,
        Dagger,
        NavigatorStaff,
        PsykerStaff,
        Shuriken,
        Splinter,
        ChainSword,
        PowerSword,
    }

    fun getWeaponType(weapon: Weapon): WeaponTypes {
        if (weapon.family == WeaponFamily.Bolt) return Bolter
        if (weapon.family == WeaponFamily.Plasma) return Plasma
        if (weapon.family == WeaponFamily.Melta) return Melta
        if (weapon.classification == WeaponClassification.Shotgun) return Shotgun
        if (weapon.classification == WeaponClassification.Sword) {
            return when (weapon.family) {
                WeaponFamily.Force -> ForceSword
                WeaponFamily.Chain -> ChainSword
                WeaponFamily.Power -> PowerSword
                else -> Sword
            }
        }
        if (weapon.classification == WeaponClassification.Chainsaw) return RockSaw
        if (weapon.classification == WeaponClassification.MaulOrHammer) return Hammer
        // Yes this is weird, but for whatever reason Arc weapons are tagged SniperRifle and snipers aren't
        if (weapon.classification == WeaponClassification.SniperRifle) return Arc
        if (weapon.family == WeaponFamily.Flame) return Flame
        if (weapon.factRequirementList.any { !it.negate && NAVIGATOR_PROFESSION_FEATURE in it.bpList }) return NavigatorStaff
        if (weapon.rateOfFire > 1 && weapon.maxRange > 1 && weapon.factRequirementList.any { !it.negate && AELDARI_PROFICIENCY in it.bpList }) return Shuriken
        if (weapon.rateOfFire > 1 && weapon.maxRange > 1 && weapon.factRequirementList.any { !it.negate && DRUKHARI_PROFICIENCY in it.bpList }) return Splinter
        if (SNIPER_RIFLE_CRIT_FEATURE in weapon.extraFactList) return Rifle
        if (weapon.abilities.any { it.abilityBp == WANDERER_PORTENT_PRECISE_SHOT }) return Rifle
        if (weapon.category == WeaponCategory.Melee && !weapon.twoHanded && weapon.dodgeReduction >= 50) return Dagger
        if (weapon.abilities.any { it.abilityBp == AXE_BLEED_ABILITY }) return Axe
        if (weapon.extraFactList.any { it in WILLPOWER_BOOST_TALENTS }) return PsykerStaff
        if (weapon.abilities.any { it.abilityBp == CHAIN_LIGHTNING_ABILITY }) return PsykerStaff
        if (weapon.abilities.any { it.abilityBp == POWER_PICK_ABILITY }) return PowerPick
        if (weapon.abilities.any { it.abilityBp == WEBBER_ABILITY }) return Webber
        if (weapon.abilities.any { it.abilityBp in DARKLIGHT_BLAST }) return BlastGun

        if (weapon.category == WeaponCategory.Pistol) return Pistol

        if (weapon.rateOfFire > 1 && weapon.maxRange > 1 && weapon.family == WeaponFamily.Solid) return Autogun
        if (weapon.rateOfFire > 1 && weapon.maxRange > 1 && weapon.family == WeaponFamily.Laser) return Lasgun
        if (weapon.abilities.any { it.abilityBp == PRECISE_SHOT }) return Rifle
        return Other
    }
}