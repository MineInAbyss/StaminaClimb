package com.mineinabyss.staminaclimb.nms

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import net.minecraft.core.Registry
import net.minecraft.network.protocol.game.ClientboundUpdateTagsPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.BlockTags
import net.minecraft.tags.TagNetworkSerialization.NetworkPayload
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftPlayer
import org.bukkit.entity.Player

object Tags {
    fun createPayload(map: Map<ResourceLocation, IntList>): NetworkPayload {
        return NetworkPayload::class.java.declaredConstructors.first()
            .also { it.isAccessible = true }
            .newInstance(map) as NetworkPayload
    }

    val disabledPlayers = mutableSetOf<Player>()

    fun enableClimb(player: Player) {
        if (!disabledPlayers.contains(player)) return
        disabledPlayers -= player

        val tags = Registry.BLOCK.tags.map { pair ->
            pair.first.location to IntArrayList(pair.second.size()).apply {
                pair.second.forEach { add(Registry.BLOCK.getId(it.value())) }
            }
        }.toList()

        val payload = createPayload(tags.toMap())
        val packet = ClientboundUpdateTagsPacket(mapOf(Registry.BLOCK_REGISTRY to payload))
        (player as CraftPlayer).handle.connection.send(packet)
    }

    fun disableClimb(player: Player) {
        if (disabledPlayers.contains(player)) return
        disabledPlayers += player

        val tags = Registry.BLOCK.tags.map { pair ->
            pair.first.location to IntArrayList(pair.second.size()).apply {
                // If the tag is CLIMBABLE, don't add any blocks to the list
                if (pair.first.location == BlockTags.CLIMBABLE.location) return@apply
                pair.second.forEach { add(Registry.BLOCK.getId(it.value())) }
            }
        }.toList()

        val payload = createPayload(tags.toMap())
        val packet = ClientboundUpdateTagsPacket(mapOf(Registry.BLOCK_REGISTRY to payload))
        (player as CraftPlayer).handle.connection.send(packet)
    }

}
