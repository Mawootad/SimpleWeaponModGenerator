package simpleweaponmodgenerator

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import proto.weapon.Weapon
import proto.weapon.Weapon.*
import proto.weapon.Weapon.WeaponAbility.AbilityType
import proto.weapon.WeaponKt.factRequirement
import proto.weapon.WeaponKt.statRestriction
import proto.weapon.WeaponKt.weaponAbility
import proto.weapon.copy
import proto.weapon.weapon
import simpleweaponmodgenerator.schema.Blueprint
import simpleweaponmodgenerator.schema.BlueprintComponent
import simpleweaponmodgenerator.schema.BlueprintComponent.LootItemsPackFixed
import simpleweaponmodgenerator.schema.BlueprintItemWeapon
import simpleweaponmodgenerator.schema.BlueprintItemWeapon.AbilityContainer.Ability
import simpleweaponmodgenerator.schema.BlueprintLoot
import simpleweaponmodgenerator.schema.BlueprintSharedVendorTable
import simpleweaponmodgenerator.schema.BlueprintUnit
import java.io.File
import java.util.*
import java.util.Collections.synchronizedList
import java.util.Collections.synchronizedMap

private val String.fromBp: String get() = removePrefix("!bp_")
private fun String.notNone(ifNotNone: (String) -> Unit) {
    if (this != NONE_TEXT) ifNotNone(this)
}

class WeaponParser(private val template: String, private val modpath: String) {
    private val errorsSynchronized = synchronizedList(mutableListOf<String>())

    val errors: List<String> by this::errorsSynchronized

    val weapons by lazy {
        val weapons = synchronizedList<Weapon>(mutableListOf())
        print("Found 0 weapons")
        parseFiles("$template/Blueprints/Weapons") {
            val weapon = parseWeapon(it)
            if (weapon != null) {
                weapons += weapon
                print("\rFound ${weapons.size} weapons")
            }
        }
        println()
        weapons
    }

    private val guidToNameMap by lazy {
        val map = synchronizedMap<String, String>(mutableMapOf())
        val subpaths = listOf(
            "Weapons",
            "Classes",
            "Backgrounds",
            "Units/Companions",
            "Equipment/CommonFeatures",
            "SoulMarks",
            "Buffs",
            "FX/AbilityFxSettings"
        )
        print("Found 0 guids")
        for (subpath in subpaths) {
            parseFiles("$template/Blueprints/$subpath") {
                val namePair = parseBpName(it)
                if (namePair != null) {
                    map += namePair.guid to it.nameWithoutExtension
                    print("\rFound ${map.size} guids")
                }
            }
        }

        if (File("$modpath/Blueprints").exists()) {
            parseFiles("$modpath/Blueprints") {
                val namePair = parseBpName(it)
                if (namePair != null) {
                    map += namePair.guid to it.nameWithoutExtension
                    print("\rFound ${map.size} guids")
                }
            }
        }
        println()
        map
    }

    val nameToGuidMap by lazy { guidToNameMap.entries.associate { it.value to it.key } }

    private val sources by lazy {
        val units = synchronizedList<Pair<String, String>>(mutableListOf())
        print("Found 0 sources")
        for (subpath in listOf("Units/NPC", "Units/Monsters", "Units/Companions", "Loot")) {
            parseFiles("$template/Blueprints/$subpath") {
                val sources = parseSource(it)
                units += sources
                if (sources.isNotEmpty()) {
                    print("\rFound ${units.size} sources")
                }
            }
        }
        println()
        units
    }

    private val localizedStrings by lazy {
        val strings = synchronizedMap<String, String>(mutableMapOf())
        print("Found 0 strings")
        parseFiles("$template/Strings/Mechanics/Blueprints/Weapons", extension = "json") {
            val pair = parseStringFile(it)
            if (pair != null) {
                strings += pair
                print("\rFound ${strings.size} strings")
            }
        }
        println()
        strings
    }

    val weaponsWithStrings by lazy {
        val sources = sources.groupBy({ it.second }, { it.first })
        weapons.map { weapon ->
            weapon.copy {
                this.name = localizedStrings[nameKey.ifEmpty { nameSharedKey }] ?: ""
                description = localizedStrings[descriptionKey.ifEmpty { descriptionSharedKey }] ?: ""

                if (ability1 != weaponAbility { }) {
                    ability1 = ability1.copy {
                        abilityBpName = guidToNameMap[abilityBp] ?: abilityBp
                        onHitActionName = guidToNameMap[onHitActions] ?: onHitActions
                        fxBpName = guidToNameMap[fxBp] ?: fxBp
                    }
                }
                if (ability2 != weaponAbility { }) {
                    ability2 = ability2.copy {
                        abilityBpName = guidToNameMap[abilityBp] ?: abilityBp
                        onHitActionName = guidToNameMap[onHitActions] ?: onHitActions
                        fxBpName = guidToNameMap[fxBp] ?: fxBp
                    }
                }
                if (ability3 != weaponAbility { }) {
                    ability3 = ability3.copy {
                        abilityBpName = guidToNameMap[abilityBp] ?: abilityBp
                        onHitActionName = guidToNameMap[onHitActions] ?: onHitActions
                        fxBpName = guidToNameMap[fxBp] ?: fxBp
                    }
                }
                if (ability4 != weaponAbility { }) {
                    ability4 = ability4.copy {
                        abilityBpName = guidToNameMap[abilityBp] ?: abilityBp
                        onHitActionName = guidToNameMap[onHitActions] ?: onHitActions
                        fxBpName = guidToNameMap[fxBp] ?: fxBp
                    }
                }
                if (ability5 != weaponAbility { }) {
                    ability5 = ability5.copy {
                        abilityBpName = guidToNameMap[abilityBp] ?: abilityBp
                        onHitActionName = guidToNameMap[onHitActions] ?: onHitActions
                        fxBpName = guidToNameMap[fxBp] ?: fxBp
                    }
                }
                this.sources += sources[guid].orEmpty()
                extraFactName += extraFact.map { guidToNameMap[it] ?: "" }
                val newRequiredFacts = factRequirement.map { requirement ->
                    requirement.copy {
                        bpName += bp.map { guidToNameMap[it] ?: "" }
                    }
                }
                factRequirement.clear()
                factRequirement += newRequiredFacts
            }
        }
    }

    private fun parseFiles(root: String, extension: String = "jbp", fileParser: (File) -> Unit) {
        runBlocking {
            fun runJbpRecursive(directory: File) {
                for (file in directory.listFiles()!!) {
                    if (file.isDirectory) {
                        launch { runJbpRecursive(file) }
                        continue
                    }

                    if (file.extension == extension) {
                        launch { fileParser(file) }
                    }
                }
            }

            runJbpRecursive(File(root))
        }
    }

    private fun parseBpName(file: File): Blueprint? =
        try {
            Blueprint.decode(file)
        } catch (e: Exception) {
            errorsSynchronized += "Couldn't get BP name for ${file.name}: ${e.stackTraceToString()}"
            null
        }

    private fun parseWeapon(file: File): Weapon? =
        try {
            weapon {
                val blueprintItemWeapon = Blueprint.decode(file)
                guid = blueprintItemWeapon.guid
                val data = blueprintItemWeapon.data as? BlueprintItemWeapon ?: return null

                if (!data.inGame) return null
                if (data.natural) return null
                if (data.unlootable) return null
                if (data.nonRemovable) return null

                nameKey = data.displayName.key ?: ""
                nameSharedKey = data.displayName.shared?.key ?: ""

                descriptionKey = data.description.key ?: ""
                descriptionSharedKey = data.description.shared?.key ?: ""

                fun Ability.toProto() = weaponAbility {
                    val ability = this@toProto
                    ability.type.notNone { this.type = AbilityType.valueOf(it) }
                    ability.bp?.let { abilityBp = it.fromBp }
                    ability.fx?.let { fxBp = it.fromBp }
                    ability.onHit?.let { onHitActions = it.fromBp }
                    ap = ability.ap
                }.takeIf { it.type != AbilityType.ABILITY_NONE }

                data.abilityContainer.ability1.toProto()?.let { ability1 = it }
                data.abilityContainer.ability2.toProto()?.let { ability2 = it }
                data.abilityContainer.ability3.toProto()?.let { ability3 = it }
                data.abilityContainer.ability4.toProto()?.let { ability4 = it }
                data.abilityContainer.ability5.toProto()?.let { ability5 = it }

                val statRestrictions =
                    data.components.mapNotNull { it as? BlueprintComponent.EquipmentRestrictionStat }
                if (statRestrictions.size > 1) errorsSynchronized += "Found multiple stat restrctions on $file"
                statRestrictions.firstOrNull()?.let {
                    statRestriction = statRestriction {
                        stat = it.stat
                        requirement = it.minValue
                    }
                }

                factRequirement += data.components.mapNotNull { it as? BlueprintComponent.EquipmentRestrictionHasFacts }
                    .map {
                        factRequirement {
                            all = it.all
                            negate = it.inverted
                            bp += it.facts.map { fact -> fact.fromBp }
                        }
                    }

                extraFact += data.components.mapNotNull { (it as? BlueprintComponent.AddFactToEquipmentWielder)?.fact?.fromBp }

                blueprintName = file.nameWithoutExtension
                data.category.notNone { category = WeaponCategory.valueOf(it) }
                data.family.notNone { family = WeaponFamily.valueOf(it) }
                data.classification.notNone { classification = WeaponClassification.valueOf(it) }
                heavy = data.heaviness == "Heavy"
                twoHanded = data.holdingType == "TwoHanded"
                minDamage = data.damage
                maxDamage = data.maxDamage
                penetration = data.penetration
                dodgeReduction = data.dodgePenetration
                additionalHitChance = data.additionalHitChance
                recoil = data.recoil
                maxRange = data.maxDistance
                ammo = data.maxAmmo
                rateOfFire = data.rateOfFire
            }
        } catch (e: Exception) {
            errorsSynchronized += "Failed parsing weapon $file: ${e.stackTraceToString()}"
            null
        }

    private fun parseSource(file: File): List<Pair<String, String>> {
        return try {
            val data = Blueprint.decode(file).data
            when (data) {
                is BlueprintUnit -> {
                    buildList {
                        val unitString = "Unit - ${file.parentFile.nameWithoutExtension}: ${file.nameWithoutExtension}"
                        data.body.itemEquipmentHandSettings.primaryHand?.let { add(unitString to it.fromBp) }
                        data.body.itemEquipmentHandSettings.secondaryHand?.let { add(unitString to it.fromBp) }
                        data.body.itemEquipmentHandSettings.primaryHandAlt?.let { add(unitString to it.fromBp) }
                        data.body.itemEquipmentHandSettings.secondaryHandAlt?.let { add(unitString to it.fromBp) }
                    }
                }

                is BlueprintLoot -> {
                    val lootName = "Chest - ${file.parentFile.nameWithoutExtension}: ${file.nameWithoutExtension}"
                    data.items.mapNotNull { if (it.item != null) lootName to it.item.fromBp else null }
                }

                is BlueprintSharedVendorTable -> {
                    val nameRoot = "Vendor - ${file.nameWithoutExtension}"
                    data.components.filterIsInstance<LootItemsPackFixed>().map {
                        "$nameRoot ${it.item.profitFactorCost}PF" to it.item.item.fromBp
                    }
                }

                else -> emptyList()
            }
        } catch (e: Exception) {
            errorsSynchronized += "Failed parsing source $file: ${e.stackTraceToString()}"
            emptyList()
        }
    }

    @Serializable
    private data class StringFile(val key: String, val languages: List<LanguageEntry>) {
        @Serializable
        data class LanguageEntry(val locale: String, val text: String)

        companion object {
            private val PARSER = Json { ignoreUnknownKeys = true }

            fun decode(file: File) = PARSER.decodeFromString<StringFile>(file.readText())
        }
    }

    private fun parseStringFile(file: File): Pair<String, String>? = try {
        val data = StringFile.decode(file)
        data.key to data.languages.first { it.locale == "enGB" }.text
    } catch (e: Exception) {
        errorsSynchronized += "Failed getting string from $file: ${e.stackTraceToString()}"
        null
    }
}