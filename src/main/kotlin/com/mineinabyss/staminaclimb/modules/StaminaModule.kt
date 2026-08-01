package com.mineinabyss.staminaclimb.modules

import com.mineinabyss.staminaclimb.StaminaClimbPlugin
import com.mineinabyss.staminaclimb.config.StaminaConfig
import com.mineinabyss.staminaclimb.stamina.StaminaTask
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.minecraft.core.Registry
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.TagNetworkSerialization.NetworkPayload

val stamina: StaminaClimbModule
    get() = StaminaClimbModule.instance ?: error("StaminaClimb plugin hasn't started yet!")

interface StaminaClimbModule {
    val plugin: StaminaClimbPlugin
    val config: StaminaConfig
    val staminaTask: StaminaTask
    val disabledClimbingTags: Object2ObjectOpenHashMap<ResourceKey<out Registry<*>>, NetworkPayload>
    val disabledClimbingTagsPacket: ClientboundUpdateTagsPacket
    val initialTags: Object2ObjectOpenHashMap<ResourceKey<out Registry<*>>, NetworkPayload>
    val initialTagsPacket: ClientboundUpdateTagsPacket

    companion object {
        var instance: StaminaClimbModule? = null
    }
}
