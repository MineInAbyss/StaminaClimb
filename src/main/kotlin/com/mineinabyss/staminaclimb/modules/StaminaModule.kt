package com.mineinabyss.staminaclimb.modules

import com.mineinabyss.staminaclimb.StaminaClimbPlugin
import com.mineinabyss.staminaclimb.config.StaminaConfig
import it.unimi.dsi.fastutil.ints.IntArrayList
import net.minecraft.resources.ResourceLocation

val stamina: StaminaClimbModule = TODO()

interface StaminaClimbModule {
    val plugin: StaminaClimbPlugin
    val config: StaminaConfig
    val emptyClimbableMap: Map<ResourceLocation, IntArrayList>
    val normalClimbableMap: Map<ResourceLocation, IntArrayList>
}
