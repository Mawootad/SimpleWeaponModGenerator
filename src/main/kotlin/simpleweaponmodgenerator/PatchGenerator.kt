package simpleweaponmodgenerator

import proto.weapon.Weapon
import proto.weapon.Weapon.WeaponAbility
import proto.weapon.copy
import proto.weapon.weapon
import simpleweaponmodgenerator.schema.BlueprintComponent
import simpleweaponmodgenerator.schema.BlueprintItemWeaponPatch
import simpleweaponmodgenerator.schema.PatchEntry
import simpleweaponmodgenerator.schema.patchConfigYaml
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest

private val String.toBp: String get() = "!bp_$this"

object PatchGenerator {
    fun getBpsFromNames(weapon: Weapon, nameToGuidMap: () -> Map<String, String>) = weapon.copy {
        fun getBpOrUseBpLikeString(name: String) = when {
            name.isEmpty() -> ""
            name in nameToGuidMap() -> nameToGuidMap()[name]!!
            name.matches("""[a-f0-9]{32}""".toRegex()) -> name
            else -> {
                println("Couldn't find guid for BP $name, ignoring")
                null
            }
        }

        guid = getBpOrUseBpLikeString(blueprintName) ?: return@copy

        if (extraFactName.size != extraFact.size) {
            extraFact.clear()
            extraFact += extraFactName.mapNotNull { getBpOrUseBpLikeString(it) }
        }

        fun lookupAbility(ability: WeaponAbility) = ability.copy {
            if (hasFxBpName() && !hasFxBp()) getBpOrUseBpLikeString(fxBpName)?.let { fxBp = it }
            if (hasAbilityBpName() && !hasAbilityBp()) getBpOrUseBpLikeString(abilityBpName)?.let { abilityBp = it }
            if (hasOnHitActionName() && !hasOnHitActions()) getBpOrUseBpLikeString(onHitActionName)?.let {
                onHitActions = it
            }
        }

        if (hasAbility1()) ability1 = lookupAbility(ability1)
        if (hasAbility2()) ability2 = lookupAbility(ability2)
        if (hasAbility3()) ability3 = lookupAbility(ability3)
        if (hasAbility4()) ability4 = lookupAbility(ability4)
        if (hasAbility5()) ability5 = lookupAbility(ability5)
    }

    private fun String.enumNoneToNone() = if (endsWith("_NONE")) NONE_TEXT else this

    @OptIn(ExperimentalStdlibApi::class)
    private fun stableUid(vararg identifiers: String) =
        MessageDigest.getInstance("MD5").digest(identifiers.joinToString("|").toByteArray()).toHexString()

    private fun stableUidWithDashes(vararg identifiers: String) =
        stableUid(*identifiers).replace("""(\w{8})(\w{4})(\w{4})(\w{4})(\w{12})""".toRegex(), "$1-$2-$3-$4-$5")

    fun writePatches(patches: List<Weapon>, baseline: List<Weapon>, outputDir: String) {
        var clearedFiles = false;

        if (!File("$outputDir/generated/Blueprints").exists()) {
            Files.createDirectories(Paths.get("$outputDir/generated/Blueprints"))
        }
        val baselineMap = baseline.associateBy { it.guid }

        val writtenPatches = mutableListOf<Pair<String, String>>()

        for (patch in patches) {
            if (patch == weapon { }) continue
            val baseline = baselineMap[patch.guid]
            if (baseline == null) {
                println("Cannot generate patch, cannot find baseline for guid: $patch ")
                continue
            }
            if (patch.blueprintName.isBlank()) {
                println("Cannot generate patch, missing name: $patch")
                continue
            }

            fun <T> diffOrNull(present: Boolean, value: Weapon.() -> T): T? =
                when {
                    !present -> null
                    value(patch) == value(baseline) -> null
                    else -> value(patch)
                }

            fun <T> abilityDiffOrNull(
                present: Boolean,
                selector: Weapon.() -> WeaponAbility,
                value: WeaponAbility.() -> T
            ): T? =
                when {
                    !present -> null
                    value(selector(patch)) == value(selector(baseline)) -> null
                    else -> value(selector(patch))
                }

            fun abilityBpDiff(
                present: Boolean,
                selector: Weapon.() -> WeaponAbility,
                value: WeaponAbility.() -> String
            ): String? =
                when {
                    !present -> ""
                    value(selector(patch)) == value(selector(baseline)) -> ""
                    else -> value(selector(patch)).toBp.takeIf { it != "".toBp }
                }

            fun abilityDiff(
                present: Boolean,
                selector: Weapon.() -> WeaponAbility
            ): BlueprintItemWeaponPatch.AbilityContainer.Ability = with(selector(patch)) {
                if (present) {
                    BlueprintItemWeaponPatch.AbilityContainer.Ability()
                } else {
                    BlueprintItemWeaponPatch.AbilityContainer.Ability(
                        type = abilityDiffOrNull(hasType(), selector) { type.name.enumNoneToNone() },
                        bp = abilityBpDiff(hasAbilityBp(), selector) { abilityBp },
                        fx = abilityBpDiff(hasFxBp(), selector) { fxBp },
                        onHitOverrideType = abilityDiffOrNull(
                            hasOnHitActions(),
                            selector
                        ) { if (onHitActions.isEmpty()) "None" else "Add" },
                        onHit = abilityBpDiff(hasOnHitActions(), selector) { onHitActions },
                        ap = abilityDiffOrNull(hasAp(), selector) { ap },
                    )
                }
            }

            val jsonPatch = with(patch) {
                BlueprintItemWeaponPatch(
                    category = diffOrNull(hasCategory()) { category.name.enumNoneToNone() },
                    family = diffOrNull(hasFamily()) { family.name.enumNoneToNone() },
                    classification = diffOrNull(hasClassification()) { classification.name.enumNoneToNone() },
                    heaviness = diffOrNull(hasHeavy()) { if (heavy) "Heavy" else "NotHeavy" },
                    holdingType = diffOrNull(hasTwoHanded()) { if (twoHanded) "TwoHanded" else "OneHanded" },
                    isTwoHanded = diffOrNull(hasTwoHanded()) { twoHanded },
                    damage = diffOrNull(hasMinDamage()) { minDamage },
                    maxDamage = diffOrNull(hasMaxDamage()) { maxDamage },
                    penetration = diffOrNull(hasPenetration()) { penetration },
                    dodgePenetration = diffOrNull(hasDodgeReduction()) { dodgeReduction },
                    additionalHitChance = diffOrNull(hasAdditionalHitChance()) { additionalHitChance },
                    recoil = diffOrNull(hasRecoil()) { recoil },
                    maxDistance = diffOrNull(hasMaxRange()) { maxRange },
                    maxAmmo = diffOrNull(hasAmmo()) { ammo },
                    rateOfFire = diffOrNull(hasRateOfFire()) { rateOfFire },
                    abilityContainer = BlueprintItemWeaponPatch.AbilityContainer(
                        ability1 = abilityDiff(hasAbility1()) { ability1 },
                        ability2 = abilityDiff(hasAbility2()) { ability2 },
                        ability3 = abilityDiff(hasAbility3()) { ability3 },
                        ability4 = abilityDiff(hasAbility4()) { ability4 },
                        ability5 = abilityDiff(hasAbility5()) { ability5 },
                    ),
                    components = (extraFactList subtract baseline.extraFactList.toSet()).map {
                        BlueprintItemWeaponPatch.ComponentPatch.Prepend(
                            component = BlueprintComponent.AddFactToEquipmentWielder(
                                fact = it.toBp,
                                name = "\$AddFactToEquipmentWielder\$${stableUidWithDashes(name, it)}",
                            )
                        )
                    }
                )
            }

            if (jsonPatch != BlueprintItemWeaponPatch()) {
                if (!clearedFiles) {
                    for (file in File("$outputDir/generated/Blueprints").listFiles().filter { it.endsWith(".patch") }) {
                        file.delete()
                    }
                    clearedFiles = true
                }

                println("Writing patch ${patch.blueprintName}")
                File("$outputDir/generated/Blueprints/${patch.blueprintName}.patch").writer().use {
                    it.write(jsonPatch.encode())
                }
                writtenPatches += patch.guid to patch.blueprintName
            }
        }

        if (writtenPatches.isNotEmpty()) {
            println("Writing patches")
            File("$outputDir/generated/generatedPatchesConfig.asset").writer().use {
                it.append(
                    patchConfigYaml(
                        writtenPatches.map { PatchEntry(guid = it.first, filename = it.second) },
                        "generatedPatchesConfig"
                    )
                )
            }
        }
    }
}