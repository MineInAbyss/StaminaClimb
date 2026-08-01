package com.mineinabyss.staminaclimb.modules

import com.mineinabyss.idofront.config.WriteMode
import com.mineinabyss.idofront.config.config
import com.mineinabyss.staminaclimb.StaminaClimbPlugin
import com.mineinabyss.staminaclimb.config.StaminaConfig
import com.mineinabyss.staminaclimb.stamina.StaminaTask
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.minecraft.core.Registry
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.TagNetworkSerialization.NetworkPayload
import kotlin.io.path.div

class StaminaPaperModule(
    override val plugin: StaminaClimbPlugin
) : StaminaClimbModule {
    override val config: StaminaConfig = config<StaminaConfig> {
        default = StaminaConfig()
        writeBack = WriteMode.WHEN_EMPTY
    }.single(plugin.dataPath / "config.yml").getCachedOrRead()
    override val staminaTask: StaminaTask = StaminaTask(config)
    override val disabledClimbingTags: Object2ObjectOpenHashMap<ResourceKey<out Registry<*>>, NetworkPayload> = Object2ObjectOpenHashMap()
    override val disabledClimbingTagsPacket = ClientboundUpdateTagsPacket(disabledClimbingTags)
    override val initialTags: Object2ObjectOpenHashMap<ResourceKey<out Registry<*>>, NetworkPayload> = Object2ObjectOpenHashMap()
    override val initialTagsPacket = ClientboundUpdateTagsPacket(initialTags)
}
