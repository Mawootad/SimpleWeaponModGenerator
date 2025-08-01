package simpleweaponmodgenerator

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import proto.weapon.Weapon
import proto.weapon.Weapon.*
import proto.weapon.Weapon.WeaponAbility.AbilityType
import proto.weapon.WeaponKt
import proto.weapon.WeaponKt.factRequirement
import proto.weapon.WeaponKt.statRestriction
import proto.weapon.WeaponKt.weaponAbility
import proto.weapon.copy
import proto.weapon.weapon
import simpleweaponmodgenerator.schema.Blueprint
import simpleweaponmodgenerator.schema.BlueprintComponent
import simpleweaponmodgenerator.schema.BlueprintItemWeapon
import simpleweaponmodgenerator.schema.BlueprintItemWeapon.AbilityContainer.Ability
import java.io.File
import java.util.*

private val JsonElement.stringValue: String get() = jsonPrimitive.toString().trim('"')
private val String.fromBp: String get() = removePrefix("!bp_")
private fun String.notNone(ifNotNone: (String) -> Unit) {
    if (this != NONE_TEXT) ifNotNone(this)
}

class WeaponParser(private val template: String, private val modpath: String) {
    val weapons by lazy {
        val weapons = Collections.synchronizedList<Weapon>(mutableListOf())
        parseFiles("$template/Blueprints/Weapons") {
            println("Parsing weapon $it")
            val weapon = parseWeapon(it)
            if (weapon != null) weapons += weapon
        }
        weapons
    }

    private val guidToNameMap by lazy {
        val map = Collections.synchronizedMap<String, String>(mutableMapOf())
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
        for (subpath in subpaths) {
            parseFiles("$template/Blueprints/$subpath") {
                println("Getting guid for $it")
                val namePair = parseBpName(it)
                if (namePair != null) map += namePair.guid to it.nameWithoutExtension
            }

            if (File("$modpath/Blueprints").exists()) {
                parseFiles("$modpath/Blueprints") {
                    println("Getting guid for $it")
                    val namePair = parseBpName(it)
                    if (namePair != null) map += namePair.guid to it.nameWithoutExtension
                }
            }
        }
        map
    }

    val nameToGuidMap by lazy { guidToNameMap.entries.associate { it.value to it.key } }

    private val locations by lazy {
        val locations = Collections.synchronizedList<Pair<String, String>>(mutableListOf())
        parseFiles("$template/Blueprints/Loot") {
            println("Parsing location $it")
            locations += parseLocation(it)
        }
        locations
    }

    private val units by lazy {
        val units = Collections.synchronizedList<Pair<String, String>>(mutableListOf())
        for (subpath in listOf("NPC", "Monsters", "Companions")) {
            parseFiles("$template/Blueprints/Units/$subpath") {
                println("Parsing unit $it")
                units += parseUnitLoot(it)
            }
        }
        units
    }

    private val vendors by lazy {
        val vendors = Collections.synchronizedList<Pair<String, String>>(mutableListOf())
        parseFiles("$template/Blueprints/Loot/VendorTables") {
            println("Parsing vendor $it")
            vendors += parseVendor(it)
        }
        vendors
    }

    private val localizedStrings by lazy {
        val strings = Collections.synchronizedMap<String, String>(mutableMapOf())
        parseFiles("$template/Strings/Mechanics/Blueprints/Weapons", extension = "json") {
            println("Getting string for $it")
            val pair = parseStringFile(it)
            if (pair != null) strings += pair
        }
        strings
    }

    val weaponsWithStrings by lazy {
        val sources = (locations + units + vendors).groupBy({ it.second.fromBp }, { it.first })
        weapons.map { weapon ->
            weapon.copy {
                this.name = localizedStrings[nameKey.ifEmpty { nameSharedKey }] ?: ""
                description = localizedStrings[descriptionKey.ifEmpty { descriptionSharedKey }] ?: ""

                if (ability1 != WeaponKt.weaponAbility { }) {
                    ability1 = ability1.copy {
                        abilityBpName = guidToNameMap[abilityBp] ?: abilityBp
                        onHitActionName = guidToNameMap[onHitActions] ?: onHitActions
                        fxBpName = guidToNameMap[fxBp] ?: fxBp
                    }
                }
                if (ability2 != WeaponKt.weaponAbility { }) {
                    ability2 = ability2.copy {
                        abilityBpName = guidToNameMap[abilityBp] ?: abilityBp
                        onHitActionName = guidToNameMap[onHitActions] ?: onHitActions
                        fxBpName = guidToNameMap[fxBp] ?: fxBp
                    }
                }
                if (ability3 != WeaponKt.weaponAbility { }) {
                    ability3 = ability3.copy {
                        abilityBpName = guidToNameMap[abilityBp] ?: abilityBp
                        onHitActionName = guidToNameMap[onHitActions] ?: onHitActions
                        fxBpName = guidToNameMap[fxBp] ?: fxBp
                    }
                }
                if (ability4 != WeaponKt.weaponAbility { }) {
                    ability4 = ability4.copy {
                        abilityBpName = guidToNameMap[abilityBp] ?: abilityBp
                        onHitActionName = guidToNameMap[onHitActions] ?: onHitActions
                        fxBpName = guidToNameMap[fxBp] ?: fxBp
                    }
                }
                if (ability5 != WeaponKt.weaponAbility { }) {
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
            println("Couldn't get BP name for ${file.name}: ${e.stackTraceToString()}")
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

                nameKey = data.displayName.key
                nameSharedKey = data.displayName.shared?.key ?: ""

                descriptionKey = data.description.key
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
                if (statRestrictions.size > 1) println("Found multiple stat restrctions on $file")
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
            println("Failed parsing weapon $file: ${e.stackTraceToString()}")
            null
        }

    private fun parseUnitLoot(file: File): List<Pair<String, String>> {
        return try {
            val json = Json.Default.parseToJsonElement(file.readText()).jsonObject
            val data = json["Data"]!!.jsonObject
            if (!(data["\$type"]?.stringValue!!.endsWith("BlueprintUnit"))) return emptyList()
            val body = data["Body"]?.jsonObject ?: return emptyList()
            val unit = "Unit - ${file.parentFile.nameWithoutExtension}: ${file.nameWithoutExtension}"

            val settings = body["ItemEquipmentHandSettings"]?.jsonObject ?: return emptyList()

            buildList {
                this += body.values.filter { it is JsonPrimitive }.map { entry -> unit to entry.stringValue }
                if (settings["m_PrimaryHand"] is JsonPrimitive) this += unit to settings["m_PrimaryHand"]!!.stringValue
                if (settings["m_SecondaryHand"] is JsonPrimitive) this += unit to settings["m_SecondaryHand"]!!.stringValue
                if (settings["m_PrimaryHandAlternative1"] is JsonPrimitive) this += unit to settings["m_PrimaryHandAlternative1"]!!.stringValue
                if (settings["m_SecondaryHandAlternative1"] is JsonPrimitive) this += unit to settings["m_SecondaryHandAlternative1"]!!.stringValue
            }
        } catch (e: Exception) {
            println("Failed getting unit $file: ${e.stackTraceToString()}")
            emptyList()
        }
    }

    private fun parseLocation(file: File): List<Pair<String, String>> {
        return try {
            val json = Json.Default.parseToJsonElement(file.readText()).jsonObject
            val data = json["Data"]!!.jsonObject
            if (!(data["\$type"]?.stringValue!!.endsWith("BlueprintLoot"))) return emptyList()
            val location = "Chest - ${file.parentFile.nameWithoutExtension}: ${file.nameWithoutExtension}"
            val items = data["Items"]?.jsonArray ?: return emptyList()

            items.map { location to it.jsonObject["m_Item"]!!.stringValue }
        } catch (e: Exception) {
            println("Failed getting location $file: ${e.stackTraceToString()}")
            emptyList()
        }
    }


    private fun parseVendor(file: File): List<Pair<String, String>> {
        return try {
            val json = Json.Default.parseToJsonElement(file.readText()).jsonObject
            val data = json["Data"]!!.jsonObject
            if (!(data["\$type"]?.stringValue!!.endsWith("BlueprintSharedVendorTable"))) return emptyList()
            val vendor = "Vendor - ${file.nameWithoutExtension}"
            val items = data["Components"]?.jsonArray?.filter {
                it.jsonObject["\$type"]?.stringValue!!.endsWith("LootItemsPackFixed")
            } ?: return emptyList()

            items.map { entry ->
                (vendor + " ${entry.jsonObject["m_Item"]!!.jsonObject["m_ProfitFactorCost"]?.jsonPrimitive?.int ?: 0}PF") to
                        entry.jsonObject["m_Item"]!!.jsonObject["m_Item"]!!.stringValue
            }
        } catch (e: Exception) {
            println("Failed getting location $file: ${e.stackTraceToString()}")
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
        println("Failed getting string from $file: ${e.stackTraceToString()}")
        null
    }
}