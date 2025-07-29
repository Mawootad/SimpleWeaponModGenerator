package simpleweaponmodgenerator

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import proto.weapon.Weapon
import proto.weapon.Weapon.WeaponAbility
import proto.weapon.WeaponKt
import proto.weapon.copy
import proto.weapon.weapon
import java.io.File
import java.util.Collections

private val JsonElement.stringValue: String get() = jsonPrimitive.toString().trim('"')
private val JsonElement.stringValueOrNull: String?
    get() = if (this is JsonNull) null else jsonPrimitive.toString().trim('"')
private val String.fromBp: String get() = removePrefix("!bp_")

private fun <T> JsonElement.parseEnum(valueOf: (String) -> T): T? {
    val value = stringValue
    return if (value == "None") null else valueOf(value)
}

class WeaponParser(private val template: String) {
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
                if (namePair != null) map += namePair
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

    private fun parseBpName(file: File): Pair<String, String>? =
        try {
            val json = Json.Default.parseToJsonElement(file.readText()).jsonObject
            json["AssetId"]!!.stringValue to file.nameWithoutExtension
        } catch (e: Exception) {
            println("Couldn't get BP name for ${file.name}: ${e.stackTraceToString()}")
            null
        }

    private fun parseWeapon(file: File): Weapon? =
        try {
            weapon {
                val json = Json.Default.parseToJsonElement(file.readText()).jsonObject
                guid = json["AssetId"]!!.stringValue
                val data = json["Data"]!!.jsonObject
                if (!(data["\$type"]?.stringValue!!.endsWith("BlueprintItemWeapon"))) return null
                if (data["CanBeUsedInGame"]?.jsonPrimitive?.boolean != true) return null
                if (data["m_IsNatural"]?.jsonPrimitive?.boolean == true) return null
                if (data["IsUnlootable"]?.jsonPrimitive?.boolean == true) return null
                if (data["IsNonRemovable"]?.jsonPrimitive?.boolean == true) return null

                nameKey = data["m_DisplayName"]?.jsonObject?.get("m_Key")?.stringValue ?: ""
                nameSharedKey =
                    (data["m_DisplayName"]?.jsonObject?.get("Shared") as? JsonObject)?.get("stringkey")?.stringValue
                        ?: ""

                descriptionKey = data["m_Description"]?.jsonObject?.get("m_Key")?.stringValue ?: ""
                descriptionSharedKey =
                    (data["m_Description"]?.jsonObject?.get("Shared") as? JsonObject)?.get("stringkey")?.stringValue
                        ?: ""

                val abilityContainer = data["AbilityContainer"]?.jsonObject?.let {
                    List(5) { n -> it["Ability${n + 1}"]?.jsonObject?.takeIf { ability -> ability["Type"]!!.stringValue != "None" } }
                } ?: List(5) { null }

                val abilities = abilityContainer
                    .map { ability ->
                        if (ability == null) return@map null

                        WeaponKt.weaponAbility {
                            ability["Type"]!!.parseEnum(Weapon.WeaponAbility.AbilityType::valueOf)?.let { type = it }
                            abilityBp = ability["m_Ability"]!!.stringValue.fromBp
                            ability["m_OnHitActions"]?.stringValueOrNull?.fromBp?.let { onHitActions = it }
                            ability["m_FXSettings"]?.stringValueOrNull?.fromBp?.let { fxBp = it }
//                            ability["OnHitOverrideType"]?.parseEnum(Weapon.WeaponAbility.OverrideType::valueOf)?.let {
//                                onHitOverrideType = it
//                            }
                            ap = ability["AP"]!!.jsonPrimitive.int
                        }
                    }

                abilities[0]?.let { ability1 = it }
                abilities[1]?.let { ability2 = it }
                abilities[2]?.let { ability3 = it }
                abilities[3]?.let { ability4 = it }
                abilities[4]?.let { ability5 = it }

                val components = data["Components"]?.jsonArray?.map { it.jsonObject }

                components?.firstOrNull { it["\$type"]!!.stringValue.endsWith("EquipmentRestrictionStat") }
                    ?.let {
                        this.statRestriction =
                            WeaponKt.statRestriction {
                                stat = it["Stat"]!!.stringValue
                                requirement = it["MinValue"]!!.jsonPrimitive.int
                            }
                    }


                factRequirement += components?.filter { it["\$type"]!!.stringValue.endsWith("EquipmentRestrictionHasFacts") }
                    ?.map {
                        WeaponKt.factRequirement {
                            all = it["All"]!!.jsonPrimitive.boolean
                            negate = it["m_Inverted"]!!.jsonPrimitive.boolean
                            bp += it["m_Facts"]!!.jsonArray.map { fact -> fact.stringValue.fromBp }
                        }
                    }.orEmpty()

                extraFact += components?.filter { it["\$type"]!!.stringValue.endsWith("AddFactToEquipmentWielder") }
                    ?.map {
                        it["m_Fact"]!!.stringValue.fromBp
                    }.orEmpty()

                blueprintName = file.nameWithoutExtension
                data["Category"]?.parseEnum(Weapon.WeaponCategory::valueOf)?.let { category = it }
                data["Family"]?.parseEnum(Weapon.WeaponFamily::valueOf)?.let { family = it }
                data["Classification"]?.parseEnum(Weapon.WeaponClassification::valueOf)?.let { classification = it }
                heavy = data["m_Heaviness"]?.stringValue == "Heavy"
                twoHanded = data["m_HoldingType"]?.stringValue == "TwoHanded"
                data["WarhammerDamage"]?.jsonPrimitive?.int?.let { minDamage = it }
                data["WarhammerMaxDamage"]?.jsonPrimitive?.int?.let { maxDamage = it }
                data["WarhammerPenetration"]?.jsonPrimitive?.int?.let { penetration = it }
                data["DodgePenetration"]?.jsonPrimitive?.int?.let { dodgeReduction = it }
                data["AdditionalHitChance"]?.jsonPrimitive?.int?.let { additionalHitChance = it }
                data["WarhammerRecoil"]?.jsonPrimitive?.int?.let { recoil = it }
                data["WarhammerMaxDistance"]?.jsonPrimitive?.int?.let { maxRange = it }
                data["WarhammerMaxAmmo"]?.jsonPrimitive?.int?.let { ammo = it }
                data["RateOfFire"]?.jsonPrimitive?.int?.let { rateOfFire = it }
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

    private fun parseStringFile(file: File): Pair<String, String>? = try {
        val json = Json.Default.parseToJsonElement(file.readText()).jsonObject
        val key = json["key"]!!.stringValue
        key to json["languages"]!!.jsonArray.first { it.jsonObject["locale"]?.stringValue == "enGB" }.jsonObject["text"]!!.stringValue
    } catch (e: Exception) {
        println("Failed getting string from $file: ${e.stackTraceToString()}")
        null
    }
}