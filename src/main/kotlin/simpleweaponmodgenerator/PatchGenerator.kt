package simpleweaponmodgenerator

import proto.weapon.Weapon
import proto.weapon.Weapon.WeaponAbility
import proto.weapon.Weapon.WeaponAbility.AbilityType
import proto.weapon.weapon
import kotlin.text.isNotEmpty
import kotlinx.serialization.json.*
import proto.weapon.copy
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import kotlin.collections.contains

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

    private val JSON_FORMAT = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun stableUid(vararg identifiers: String) =
        MessageDigest.getInstance("MD5").digest(identifiers.joinToString("|").toByteArray()).toHexString()

    private fun stableUidWithDashes(vararg identifiers: String) =
        stableUid(*identifiers).replace("""(\w{8})(\w{4})(\w{4})(\w{4})(\w{12})""".toRegex(), "$1-$2-$3-$4-$5")

    fun writePatches(patches: List<Weapon>, baseline: List<Weapon>, outputDir: String) {
        if (!File("$outputDir/Blueprints").exists()) {
            Files.createDirectories(Paths.get("$outputDir/Blueprints"))
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

            fun <T> handleDiff(present: (Weapon) -> Boolean, value: (Weapon) -> T, action: (T) -> Unit) {
                if (!present(patch)) return
                if (value(patch) == value(baseline)) return
                action(value(patch))
            }

            val patchEntries = buildJsonObject {
                handleDiff(Weapon::hasCategory, Weapon::category) { put("Category", it.name.enumNoneToNone()) }
                handleDiff(Weapon::hasFamily, Weapon::family) { put("Family", it.name.enumNoneToNone()) }
                handleDiff(Weapon::hasClassification, Weapon::classification) {
                    put("Classification", it.name.enumNoneToNone())
                }
                handleDiff(Weapon::hasHeavy, Weapon::heavy) { put("m_Heaviness", if (it) "Heavy" else "NotHeavy") }
                handleDiff(Weapon::hasTwoHanded, Weapon::twoHanded) {
                    put("m_HoldingType", if (it) "TwoHanded" else "OneHanded")
                    put("IsTwoHanded", it)
                }
                handleDiff(Weapon::hasMinDamage, Weapon::minDamage) { put("WarhammerDamage", it) }
                handleDiff(Weapon::hasMaxDamage, Weapon::maxDamage) { put("WarhammerMaxDamage", it) }
                handleDiff(Weapon::hasPenetration, Weapon::penetration) { put("WarhammerPenetration", it) }
                handleDiff(Weapon::hasDodgeReduction, Weapon::dodgeReduction) { put("DodgePenetration", it) }
                handleDiff(Weapon::hasAdditionalHitChance, Weapon::additionalHitChance) {
                    put("AdditionalHitChance", it)
                }
                handleDiff(Weapon::hasRecoil, Weapon::recoil) { put("WarhammerRecoil", it) }
                handleDiff(Weapon::hasMaxRange, Weapon::maxRange) { put("WarhammerMaxDistance", it) }
                handleDiff(Weapon::hasAmmo, Weapon::ammo) { put("WarhammerMaxAmmo", it) }
                handleDiff(Weapon::hasRateOfFire, Weapon::rateOfFire) { put("RateOfFire", it) }
                val abilities = buildJsonObject {
                    handleDiff(Weapon::hasAbility1, Weapon::ability1) {
                        abilityDiffObject(it, baseline.ability1)?.let { abilityPatch -> put("Ability1", abilityPatch) }
                    }
                    handleDiff(Weapon::hasAbility1, Weapon::ability2) {
                        abilityDiffObject(it, baseline.ability2)?.let { abilityPatch -> put("Ability2", abilityPatch) }
                    }
                    handleDiff(Weapon::hasAbility1, Weapon::ability3) {
                        abilityDiffObject(it, baseline.ability3)?.let { abilityPatch -> put("Ability3", abilityPatch) }
                    }
                    handleDiff(Weapon::hasAbility1, Weapon::ability4) {
                        abilityDiffObject(it, baseline.ability4)?.let { abilityPatch -> put("Ability4", abilityPatch) }
                    }
                    handleDiff(Weapon::hasAbility1, Weapon::ability5) {
                        abilityDiffObject(it, baseline.ability5)?.let { abilityPatch -> put("Ability5", abilityPatch) }
                    }
                }
                if (abilities.isNotEmpty()) {
                    put("AbilityContainer", abilities)
                }

                val newFacts = (patch.extraFactList subtract baseline.extraFactList) - ""
                if (newFacts.isNotEmpty()) {
                    putJsonArray("Components") {
                        addAll(newFacts.map {
                            buildJsonObject {
                                put("PatchType", "Prepend")
                                putJsonObject("NewElement") {
                                    put("\$type", "65221a9a6133bd0408b019b86642d97e, AddFactToEquipmentWielder")
                                    put("name", "\$AddFactToEquipmentWielder\$${stableUidWithDashes(patch.name, it)}")
                                    put("m_Flags", 0)
                                    putJsonObject("PrototypeLink") {
                                        put("guid", "")
                                        put("name", "")
                                    }
                                    putJsonArray("m_Overrides") {}
                                    put("m_Fact", "!bp_$it")
                                }
                            }
                        })
                    }
                }
            }

            if (patchEntries.isNotEmpty()) {
                File("$outputDir/Blueprints/${patch.blueprintName}.patch").writer()
                    .use { it.append(JSON_FORMAT.encodeToString(patchEntries)) }
                writtenPatches += patch.guid to patch.blueprintName
            }
        }

        if (writtenPatches.isNotEmpty()) {
            File("$outputDir/generatedPatchesConfig.asset").writer().use {
                it.append(
                    patchConfigYaml(
                        writtenPatches.map { PatchEntry(guid = it.first, filename = it.second) },
                        "generatedPatchesConfig"
                    )
                )
            }
        }
    }

    private fun abilityDiffObject(patch: WeaponAbility, baseline: WeaponAbility): JsonObject? {
        val abilityPatch = buildJsonObject {
            if (patch.hasType() && patch.type == AbilityType.ABILITY_NONE) {
                put("Type", JsonPrimitive(NONE_TEXT))
            } else {
                if (patch.hasAp() && patch.ap != baseline.ap) put("AP", patch.ap)
                if (patch.hasAbilityBp() && patch.abilityBp != baseline.abilityBp) put(
                    "m_Ability",
                    patch.abilityBp
                )
                if (patch.hasType() && patch.type != baseline.type) put(
                    "Type",
                    patch.type.name.enumNoneToNone()
                )
                if (patch.hasFxBp() && patch.fxBp != baseline.fxBp) put(
                    "m_FXSettings",
                    patch.fxBp.takeIf { it.isNotEmpty() }
                )
//                if (patch.hasOnHitOverrideType() && patch.onHitOverrideType != baseline.onHitOverrideType) put(
//                    "OnHitOverrideType",
//                    patch.onHitOverrideType.name.enumNoneToNone()
//                )
                if (patch.hasOnHitActions() && patch.onHitActions != baseline.onHitActions) {
                    put(
                        "m_OnHitActions",
                        patch.onHitActions.takeIf { it.isNotEmpty() }
                    )
                    if (patch.onHitActions.isEmpty()) put("OnHitOverrideType", "None")
                    else if (baseline.onHitActions.isEmpty()) put("OnHitOverrideType", "Add")
                }
            }
        }
        return abilityPatch.takeIf { it.isNotEmpty() }
    }
}