package simpleweaponmodgenerator.schema

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import simpleweaponmodgenerator.schema.Blueprint.BPData
import java.io.File

private const val TYPE = "\$type"

@Serializable
data class Blueprint(@SerialName("AssetId") val guid: String, @SerialName("Data") val data: BPData) {
    @Serializable
    sealed class BPData

    @Serializable
    private class UnknownBP(@SerialName(TYPE) val type: String) : BPData()

    companion object {
        val JSON_PARSER = Json {
            serializersModule = SerializersModule {
                polymorphic(BPData::class) {
                    subclass(BlueprintItemWeapon::class)
                    subclass(BlueprintUnit::class)
                    subclass(BlueprintLoot::class)
                    subclass(BlueprintSharedVendorTable::class)
                    defaultDeserializer { UnknownBP.serializer() }
                }
                BlueprintComponent.POLYMORPHISM(this)
            }
            classDiscriminator = "\$type"
            ignoreUnknownKeys = true
        }

        fun decode(file: File) = JSON_PARSER.decodeFromString<Blueprint>(file.readText())
    }
}

@Serializable
data class BlueprintItemWeaponPatch(
    @SerialName("Category") val category: String? = null,
    @SerialName("Family") val family: String? = null,
    @SerialName("Classification") val classification: String? = null,
    @SerialName("m_Heaviness") val heaviness: String? = null,
    @SerialName("m_HoldingType") val holdingType: String? = null,
    @SerialName("IsTwoHanded") val isTwoHanded: Boolean? = null,
    @SerialName("WarhammerDamage") val damage: Int? = null,
    @SerialName("WarhammerMaxDamage") val maxDamage: Int? = null,
    @SerialName("WarhammerPenetration") val penetration: Int? = null,
    @SerialName("DodgePenetration") val dodgePenetration: Int? = null,
    @SerialName("AdditionalHitChance") val additionalHitChance: Int? = null,
    @SerialName("WarhammerRecoil") val recoil: Int? = null,
    @SerialName("WarhammerMaxDistance") val maxDistance: Int? = null,
    @SerialName("WarhammerMaxAmmo") val maxAmmo: Int? = null,
    @SerialName("RateOfFire") val rateOfFire: Int? = null,

    @SerialName("AbilityContainer") val abilityContainer: AbilityContainer = AbilityContainer(),
    @SerialName("Components") val components: List<ComponentPatch> = emptyList(),
) {
    @Serializable
    data class AbilityContainer(
        @SerialName("Ability1") val ability1: Ability = Ability(),
        @SerialName("Ability2") val ability2: Ability = Ability(),
        @SerialName("Ability3") val ability3: Ability = Ability(),
        @SerialName("Ability4") val ability4: Ability = Ability(),
        @SerialName("Ability5") val ability5: Ability = Ability(),
    ) {
        @Serializable
        data class Ability(
            @SerialName("Type") val type: String? = null,
            @SerialName("m_Ability") val bp: String? = "",
            @SerialName("m_FXSettings") val fx: String? = "",
            @SerialName("OnHitOverrideType") val onHitOverrideType: String? = null,
            @SerialName("m_OnHitActions") val onHit: String? = "",
            @SerialName("AP") val ap: Int? = null,
        )
    }

    @Serializable
    @JsonClassDiscriminator("PatchType")
    sealed class ComponentPatch {
        @Serializable
        @SerialName("Prepend")
        data class Prepend(
            @SerialName("NewElement") val component: BlueprintComponent,
        ) : ComponentPatch() {
        }
    }

    fun encode() = JSON_ENCODER.encodeToString(this)

    companion object {
        val JSON_ENCODER = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }
    }
}

@Serializable
@SerialName("c00f723cccf2d314198c42a572c631fd, BlueprintItemWeapon")
data class BlueprintItemWeapon(
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

    ) : BPData() {
    @Serializable
    data class AbilityContainer(
        @SerialName("Ability1") val ability1: Ability,
        @SerialName("Ability2") val ability2: Ability,
        @SerialName("Ability3") val ability3: Ability,
        @SerialName("Ability4") val ability4: Ability,
        @SerialName("Ability5") val ability5: Ability,
    ) {
        @Serializable
        data class Ability(
            @SerialName("Type") val type: String,
            @SerialName("m_Ability") val bp: String?,
            @SerialName("m_FXSettings") val fx: String?,
            @SerialName("m_OnHitActions") val onHit: String?,
            @SerialName("AP") val ap: Int,
        )
    }
}

@Serializable
data class PrototypeLink(val guid: String, val name: String) {
    constructor() : this("", "")
}

@Serializable
@JsonClassDiscriminator(TYPE)
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
    data class AddFactToEquipmentWielder(
        val name: String,
        @SerialName("m_Flags") val flags: Int,
        @SerialName("PrototypeLink") val prototypeLink: PrototypeLink,
        @SerialName("m_Overrides") val overrides: List<String>,
        @SerialName("m_Fact") val fact: String,
    ) : BlueprintComponent() {
        constructor(name: String, fact: String) : this(
            name = name,
            fact = fact,
            flags = 0,
            prototypeLink = PrototypeLink(),
            overrides = emptyList()
        )
    }

    @Serializable
    @SerialName("9a9cba603f85c634690eb67962fdf792, LootItemsPackFixed")
    data class LootItemsPackFixed(@SerialName("m_Item") val item: Item) : BlueprintComponent() {
        @Serializable
        data class Item(
            @SerialName("m_Item") val item: String,
            @SerialName("m_ProfitFactorCost") val profitFactorCost: Int,
        )
    }

    companion object {
        val POLYMORPHISM: SerializersModuleBuilder.() -> Unit = {
            polymorphic(BlueprintComponent::class) {
                subclass(EquipmentRestrictionStat::class)
                subclass(EquipmentRestrictionHasFacts::class)
                subclass(AddFactToEquipmentWielder::class)
                subclass(LootItemsPackFixed::class)
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

@Serializable
@SerialName("fa4fa7e4548127a47a2846c91b051065, BlueprintUnit")
data class BlueprintUnit(
    @SerialName("Body")
    val body: Body
) : BPData() {
    @Serializable
    data class Body(
        @SerialName("ItemEquipmentHandSettings") val itemEquipmentHandSettings: ItemEquipmentHandSettings,
        @SerialName("Body") val body: Map<String, String>,
    ) {
        @Serializable
        data class ItemEquipmentHandSettings(
            @SerialName("m_PrimaryHand") val primaryHand: String,
            @SerialName("m_SecondaryHand") val secondaryHand: String,
            @SerialName("m_PrimaryHandAlternative1") val primaryHandAlt: String,
            @SerialName("m_SecondaryHandAlternative1") val secondaryHandAlt: String,
        )
    }
}

@Serializable
@SerialName("0449d0493fd70da4ba79ef76be174b92, BlueprintLoot")
data class BlueprintLoot(@SerialName("Items") val items: List<Item>) : BPData() {
    @Serializable
    data class Item(@SerialName("m_Item") val item: String)
}

@Serializable
@SerialName("ccc43623dd9341449b5d07be1dabaa23, BlueprintSharedVendorTable")
data class BlueprintSharedVendorTable(@SerialName("Components") val components: List<BlueprintComponent>) : BPData()
