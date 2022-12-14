package com.mineinabyss.staminaclimb.modules

import com.mineinabyss.idofront.config.IdofrontConfig
import com.mineinabyss.idofront.di.DI
import com.mineinabyss.staminaclimb.StaminaClimbPlugin
import com.mineinabyss.staminaclimb.config.StaminaConfig
import it.unimi.dsi.fastutil.ints.IntArrayList
import net.minecraft.resources.ResourceLocation

val stamina: StaminaClimbModule by DI.observe()

interface StaminaClimbModule {
    val plugin: StaminaClimbPlugin
    val configHolder: IdofrontConfig<StaminaConfig>
    val config: StaminaConfig
    val emptyClimbableMap: Map<ResourceLocation, IntArrayList>
    val normalClimbableMap: Map<ResourceLocation, IntArrayList>
}
