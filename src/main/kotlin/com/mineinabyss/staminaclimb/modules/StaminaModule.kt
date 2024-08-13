package com.mineinabyss.staminaclimb.modules

import com.mineinabyss.idofront.di.DI
import com.mineinabyss.staminaclimb.StaminaClimbPlugin
import com.mineinabyss.staminaclimb.config.StaminaConfig
import com.mineinabyss.staminaclimb.stamina.StaminaTask
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.TagNetworkSerialization.NetworkPayload

val stamina: StaminaClimbModule by DI.observe()

interface StaminaClimbModule {
    val plugin: StaminaClimbPlugin
    val config: StaminaConfig
    val staminaTask: StaminaTask
    val disabledClimbingTags: MutableMap<ResourceKey<out Registry<*>?>, NetworkPayload>
    val initialTags: MutableMap<ResourceKey<out Registry<*>?>, NetworkPayload>
}
