package com.mineinabyss.staminaclimb.nms

import com.mineinabyss.staminaclimb.modules.stamina
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.BlockTags
import net.minecraft.tags.TagNetworkSerialization.NetworkPayload
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player

object Tags {
    fun createPayload(map: Map<ResourceLocation, IntList>): NetworkPayload =
        NetworkPayload::class.java.declaredConstructors.first()
            .also { it.isAccessible = true }
            .newInstance(map) as NetworkPayload

    val disabledPlayers = mutableSetOf<Player>()

    fun enableClimb(player: Player) {
        if (player !in disabledPlayers) return
        disabledPlayers.remove(player)
        (player as CraftPlayer).handle.connection.send(updateTagPacket(true))
    }

    fun disableClimb(player: Player) {
        if (player in disabledPlayers) return
        disabledPlayers.add(player)

        (player as CraftPlayer).handle.connection.send(updateTagPacket(false))
    }

    private fun updateTagPacket(enable: Boolean): ClientboundUpdateTagsPacket {
        return ClientboundUpdateTagsPacket(
            mapOf(
                Registries.BLOCK to createPayload(
                    when (enable) {
                        true -> stamina.normalClimbableMap
                        false -> stamina.emptyClimbableMap
                    }
                )
            )
        )
    }

    fun emptyFallDamageResetTag(player: Player): Map<ResourceLocation, IntArrayList> {
        return BuiltInRegistries.BLOCK.tags.map { pair ->
            pair.first.location to IntArrayList(pair.second.size()).apply {
                if (pair.first.location == BlockTags.FALL_DAMAGE_RESETTING.location) return@apply
                if (player in disabledPlayers && pair.first.location == BlockTags.CLIMBABLE.location) return@apply
                pair.second.forEach { add(BuiltInRegistries.BLOCK.getId(it.value())) }
            }
        }.toList().toMap()
    }

    fun createNormalClimbableMap(): Map<ResourceLocation, IntArrayList> {
        return BuiltInRegistries.BLOCK.tags.map { pair ->
            pair.first.location to IntArrayList(pair.second.size()).apply {
                if (pair.first.location == BlockTags.FALL_DAMAGE_RESETTING.location) return@apply
                pair.second.forEach { add(BuiltInRegistries.BLOCK.getId(it.value())) }
            }
        }.toList().toMap()
    }

    fun createEmptyClimbableMap(): Map<ResourceLocation, IntArrayList> {
        return BuiltInRegistries.BLOCK.tags.map { pair ->
            pair.first.location to IntArrayList(pair.second.size()).apply {
                // If the tag is CLIMBABLE, don't add any blocks to the list
                when (pair.first.location) {
                    BlockTags.CLIMBABLE.location, BlockTags.FALL_DAMAGE_RESETTING.location -> return@apply
                    else -> pair.second.forEach { add(BuiltInRegistries.BLOCK.getId(it.value())) }
                }
            }
        }.toList().toMap()
    }
}
