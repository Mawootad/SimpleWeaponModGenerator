package simpleweaponmodgenerator

import com.google.protobuf.TextFormat
import proto.weapon.Weapon
import proto.weapon.Weapon.WeaponAbility
import proto.weapon.WeaponKt.weaponAbility
import proto.weapon.WeaponList
import proto.weapon.copy
import java.io.File

object RemoveDuplicateInfo {
    fun removeaAllDuplicateInfo(patchesPath: String, baselineMap: () -> Map<String, Weapon>) {

        for (file in File(patchesPath).listFiles()!!) {
            println("Updating $file")
            val data = when (file.extension) {
                TSV -> tsvToProto(file)
                TEXTPROTO -> TextFormat.parse(file.readText(), WeaponList::class.java).weaponList
                else -> continue
            }

            val updatedWeapons = data.mapNotNull {
                val baseline = baselineMap()[it.blueprintName]
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

    fun removeDuplicateInfo(weapon: Weapon, baseline: Weapon): Weapon? =
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
}