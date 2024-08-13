package com.mineinabyss.staminaclimb.config

import kotlinx.serialization.Serializable
import net.kyori.adventure.bossbar.BossBar.Color
import net.kyori.adventure.bossbar.BossBar.Overlay
import org.bukkit.Material

@Serializable
class StaminaConfig(
    val airTime: Long = 400,
    val staminaRegen: Float = 0.01f,
    val staminaRegenInAir: Float = 0.003f,
    val staminaRemovePerTick: Float = 0.005f,
    val staminaRemoveWhileMoving: Float = 0.005f,
    val staminaRemoveWhileOnClimbable: Float = 0.01f,
    val baseBarColor: Color = Color.GREEN,
    val baseOverlay: Overlay = Overlay.NOTCHED_10,
    val barRed: Float = 0.02f,
    val barBlink1: Float = 0.3f,
    val barBlink2: Float = 0.1f,
    val barBlinkSpeed1: Float = 120f,
    val barBlinkSpeed2: Float = 20f,
    val maxFallDist: Double = 5.0,
    val jumpCooldown: Long = 300,
    val walljumpCooldown: Long = 300,
    val roofClimbDifficulty: Float = 7.0f,
    val climbBlacklist: Set<Material> = setOf(
        Material.BEACON,
        Material.BARREL,
        Material.CAMPFIRE,
        Material.CARTOGRAPHY_TABLE,
        Material.CAULDRON,
        Material.COMMAND_BLOCK,
        Material.COMPOSTER,
        Material.CRAFTING_TABLE,
        Material.BREWING_STAND,
        Material.DAYLIGHT_DETECTOR,
        Material.DISPENSER,
        Material.DROPPER,
        Material.ENCHANTING_TABLE,
        Material.ENDER_CHEST,
        Material.FARMLAND,
        Material.GRINDSTONE,
        Material.HOPPER,
        Material.HOPPER_MINECART,
        Material.ITEM_FRAME,
        Material.LANTERN,
        Material.LECTERN,
        Material.LEVER,
        Material.LOOM,
        Material.NOTE_BLOCK,
        Material.SCAFFOLDING,
        Material.SMOKER,
        Material.SMITHING_TABLE,
        Material.STONECUTTER,
        Material.SWEET_BERRY_BUSH,
        Material.BEEHIVE,
        Material.BEE_NEST,
        Material.ANVIL,
        Material.CHIPPED_ANVIL,
        Material.DAMAGED_ANVIL
    ),
    val climbBlacklistGeneral: Set<String> = setOf(
        "DOOR",
        "FENCE",
        "FURNACE",
        "_BED",
        "BELL",
        "CHEST",
        "BUTTON",
        "SIGN",
        "SHULKER_BOX"
    ),
    val climbDifficulty: Map<Material, Float> = mapOf(
        Material.SAND to 2f,
        Material.BARRIER to -1f,
        Material.COBBLESTONE to 0.8f,
        Material.ICE to 5f,
        Material.PACKED_ICE to 4f,
        Material.BLUE_ICE to 4f,
        Material.SLIME_BLOCK to 0.5f,
        Material.HONEY_BLOCK to -1f,
    ),
    val climbDifficultyGeneral: Map<String, Float> = mapOf(
        "WOOD" to 0.3f,
        "LOG" to 0.3f,
        "SMOOTH" to 5f,
        "FENCE" to 0.5f,
        "DOOR" to -1f,
        "GLASS" to -1f
    )
)
