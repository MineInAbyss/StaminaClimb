package com.mineinabyss.staminaclimb.modules

import com.mineinabyss.idofront.config.IdofrontConfig
import com.mineinabyss.idofront.config.config
import com.mineinabyss.staminaclimb.StaminaClimbPlugin
import com.mineinabyss.staminaclimb.config.StaminaConfig
import com.mineinabyss.staminaclimb.nms.Tags
import it.unimi.dsi.fastutil.ints.IntArrayList
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagNetworkSerialization.NetworkPayload

class StaminaPaperModule(
    override val plugin: StaminaClimbPlugin
) : StaminaClimbModule {
    override val config by config("config", plugin.dataPath, StaminaConfig())
    override val disabledClimbingTags: MutableMap<ResourceKey<out Registry<*>?>, NetworkPayload> = mutableMapOf()
    override val initialTags: MutableMap<ResourceKey<out Registry<*>?>, NetworkPayload> = mutableMapOf()
}
