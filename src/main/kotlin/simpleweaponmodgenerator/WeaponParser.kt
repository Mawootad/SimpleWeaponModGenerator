package simpleweaponmodgenerator

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import proto.weapon.Weapon
import proto.weapon.WeaponKt
import proto.weapon.copy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import proto.weapon.Weapon.WeaponAbility.AbilityType
import proto.weapon.Weapon.WeaponCategory
import proto.weapon.Weapon.WeaponClassification
import proto.weapon.Weapon.WeaponFamily
import proto.weapon.WeaponKt.factRequirement
import proto.weapon.WeaponKt.statRestriction
import proto.weapon.WeaponKt.weaponAbility
import proto.weapon.weapon
import java.io.File
import java.util.Collections

private val JsonElement.stringValue: String get() = jsonPrimitive.toString().trim('"')
private val JsonElement.stringValueOrNull: String?
    get() = if (this is JsonNull) null else jsonPrimitive.toString().trim('"')
private val String.fromBp: String get() = removePrefix("!bp_")
private fun String.notNone(ifNotNone: (String) -> Unit) {
    if (this != NONE_TEXT) ifNotNone(this)
}

private const val TYPE = "\$type"

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
                if (namePair != null) map += namePair.guid to it.nameWithoutExtension
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

    @Serializable
    data class SimpleJBP(@SerialName("AssetId") val guid: String, @SerialName("Data") val data: BPData) {
        @Serializable
        sealed class BPData

        @Serializable
        private class UnknownBP(@SerialName(TYPE) val type: String) : BPData()

        companion object {

            val JSON_PARSER = Json {
                serializersModule = SerializersModule {
                    polymorphic(BPData::class) {
                        subclass(BlueprintItemWeapon::class)
                        defaultDeserializer { UnknownBP.serializer() }
                    }
                    BlueprintComponent.POLYMORPHISM(this)
                }
                classDiscriminator = "\$type"
                ignoreUnknownKeys = true
            }

            fun decode(file: File) = JSON_PARSER.decodeFromString<SimpleJBP>(file.readText())
        }

    }

    private fun parseBpName(file: File): SimpleJBP? =
        try {
            SimpleJBP.decode(file)
        } catch (e: Exception) {
            println("Couldn't get BP name for ${file.name}: ${e.stackTraceToString()}")
            null
        }

    @Serializable
    @SerialName("c00f723cccf2d314198c42a572c631fd, BlueprintItemWeapon")
    private data class BlueprintItemWeapon(
        @SerialName("CanBeUsedInGame") val inGame: Boolean,
        @SerialName("m_IsNatural") val natural: Boolean,
        @SerialName("IsUnlootable") val unlootable: Boolean,
        @SerialName("IsNonRemovable") val nonRemovable: Boolean,

        @SerialName("m_DisplayName") val displayName: LocalizedString,
        @SerialName("m_Description") val description: LocalizedString,

        @SerialName("AbilityContainer") val abilityContainer: AbilityContainer,

        @SerialName("Category") val category: String,
        @SerialName("Family") val family: String,
        @SerialName("Classification") val classification: String,
        @SerialName("m_Heaviness") val heaviness: String,
        @SerialName("m_HoldingType") val holdingType: String,
        @SerialName("WarhammerDamage") val damage: Int,
        @SerialName("WarhammerMaxDamage") val maxDamage: Int,
        @SerialName("WarhammerPenetration") val penetration: Int,
        @SerialName("DodgePenetration") val dodgePenetration: Int,
        @SerialName("AdditionalHitChance") val additionalHitChance: Int,
        @SerialName("WarhammerRecoil") val recoil: Int,
        @SerialName("WarhammerMaxDistance") val maxDistance: Int,
        @SerialName("WarhammerMaxAmmo") val maxAmmo: Int,
        @SerialName("RateOfFire") val rateOfFire: Int,

        @SerialName("Components") val components: List<BlueprintComponent>,

        ) : SimpleJBP.BPData() {
        @Serializable
        data class AbilityContainer(
            @SerialName("Ability1")
            val ability1: Ability,
            @SerialName("Ability2")
            val ability2: Ability,
            @SerialName("Ability3")
            val ability3: Ability,
            @SerialName("Ability4")
            val ability4: Ability,
            @SerialName("Ability5")
            val ability5: Ability,
        ) {
            @Serializable
            data class Ability(
                @SerialName("Type")
                val type: String,
                @SerialName("m_Ability")
                val bp: String?,
                @SerialName("m_FXSettings")
                val fx: String?,
                @SerialName("m_OnHitActions")
                val onHit: String?,
                @SerialName("AP")
                val ap: Int,
            ) {
                fun toProto() = weaponAbility {
                    val ability = this@Ability
                    ability.type.notNone { this.type = AbilityType.valueOf(it) }
                    ability.bp?.let { abilityBp = it.fromBp }
                    ability.fx?.let { fxBp = it.fromBp }
                    ability.onHit?.let { onHitActions = it.fromBp }
                    ap = ability.ap
                }.takeIf { it.type != AbilityType.ABILITY_NONE }
            }
        }
    }

    @Serializable
    sealed class BlueprintComponent {
        @Serializable
        data class UnknownComponent(@SerialName(TYPE) val type: String) : BlueprintComponent()

        @Serializable
        @SerialName("6dfdda28c94860241a112b404538e2a7, EquipmentRestrictionStat")
        data class EquipmentRestrictionStat(
            @SerialName("Stat") val stat: String,
            @SerialName("MinValue") val minValue: Int,
        ) : BlueprintComponent()

        @Serializable
        @SerialName("d7b23547716f4a949471625ff6c66fb2, EquipmentRestrictionHasFacts")
        data class EquipmentRestrictionHasFacts(
            @SerialName("All") val all: Boolean,
            @SerialName("m_Inverted") val inverted: Boolean,
            @SerialName("m_Facts") val facts: List<String>,
        ) : BlueprintComponent()

        @Serializable
        @SerialName("65221a9a6133bd0408b019b86642d97e, AddFactToEquipmentWielder")
        data class AddFactToEquipmentWielder(@SerialName("m_Fact") val fact: String) : BlueprintComponent()

        companion object {
            val POLYMORPHISM: SerializersModuleBuilder.() -> Unit = {
                polymorphic(BlueprintComponent::class) {
                    subclass(EquipmentRestrictionStat::class)
                    subclass(EquipmentRestrictionHasFacts::class)
                    subclass(AddFactToEquipmentWielder::class)
                    defaultDeserializer { UnknownComponent.serializer() }
                }
            }
        }
    }

    @Serializable
    data class LocalizedString(@SerialName("m_Key") val key: String, @SerialName("Shared") val shared: Shared? = null) {
        @Serializable
        data class Shared(@SerialName("stringkey") val key: String)
    }

    private fun parseWeapon(file: File): Weapon? =
        try {
            weapon {
                val blueprintItemWeapon = SimpleJBP.decode(file)
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

                data.abilityContainer.ability1.toProto()?.let { ability1 = it }
                data.abilityContainer.ability2.toProto()?.let { ability2 = it }
                data.abilityContainer.ability3.toProto()?.let { ability3 = it }
                data.abilityContainer.ability4.toProto()?.let { ability4 = it }
                data.abilityContainer.ability5.toProto()?.let { ability5 = it }

                val statRestrictions = data.components.mapNotNull { it as? BlueprintComponent.EquipmentRestrictionStat }
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

    private fun parseStringFile(file: File): Pair<String, String>? = try {
        val json = Json.Default.parseToJsonElement(file.readText()).jsonObject
        val key = json["key"]!!.stringValue
        key to json["languages"]!!.jsonArray.first { it.jsonObject["locale"]?.stringValue == "enGB" }.jsonObject["text"]!!.stringValue
    } catch (e: Exception) {
        println("Failed getting string from $file: ${e.stackTraceToString()}")
        null
    }
}