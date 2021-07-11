package com.offz.spigot.staminaclimb.components

import com.mineinabyss.geary.ecs.api.autoscan.AutoscanComponent
import com.offz.spigot.staminaclimb.events.StaminaState
import org.bukkit.boss.BossBar

@AutoscanComponent
data class Stamina(
    val amount: Double,
    val maxAmount: Double,
    val bar: BossBar,
    val state: StaminaState,
) {
    val progress get() = amount / maxAmount
}
