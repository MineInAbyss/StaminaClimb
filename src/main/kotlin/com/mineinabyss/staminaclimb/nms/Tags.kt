package com.mineinabyss.staminaclimb.nms

import com.mineinabyss.idofront.nms.interceptClientbound
import com.mineinabyss.staminaclimb.modules.stamina
import it.unimi.dsi.fastutil.ints.IntList
import net.minecraft.core.registries.Registries
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.BlockTags
import net.minecraft.tags.TagNetworkSerialization.NetworkPayload
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player

object Tags {

    val disabledPlayers = mutableSetOf<Player>()

    /**
     * Intercepts ClientboundUpdateTagsPacket sent to players during Configuration Phase & caches it.
     *
     * It then caches the initial tag-set and generates a copy without climbable & fall_damage_resetting entries
     */
    fun interceptConfigPhaseTagPacket() {
        stamina.plugin.interceptClientbound { packet: Packet<*>, player: Player? ->
            if (packet !is ClientboundUpdateTagsPacket || player?.isOnline == true) return@interceptClientbound packet
            if (stamina.initialTags.isNotEmpty()) return@interceptClientbound packet

            stamina.initialTags.putAll(packet.tags)
            packet.tags.entries.map { registryEntry ->
                if (registryEntry.key == Registries.BLOCK) {
                    val tags = registryEntry.value.tags().map { tag ->
                        tag.key to when (tag.key) {
                            BlockTags.CLIMBABLE.location, BlockTags.FALL_DAMAGE_RESETTING.location -> IntList.of()
                            else -> tag.value
                        }
                    }.toMap()
                    registryEntry.setValue(tags.networkPayload())
                }
                registryEntry
            }.forEach {
                stamina.disabledClimbingTags[it.key] = it.value
            }

            return@interceptClientbound packet
        }
    }

    fun enableClimb(player: Player) {
        if (player !in disabledPlayers) return
        disabledPlayers.remove(player)
        (player as CraftPlayer).handle.connection.send(ClientboundUpdateTagsPacket(stamina.initialTags))
    }

    fun disableClimb(player: Player) {
        if (player in disabledPlayers) return
        disabledPlayers.add(player)

        (player as CraftPlayer).handle.connection.send(ClientboundUpdateTagsPacket(stamina.disabledClimbingTags))
    }

    private val tagsField = NetworkPayload::class.java.getDeclaredField("tags").also { it.isAccessible = true }
    private val payloadConstructor = NetworkPayload::class.java.declaredConstructors.first().also { it.isAccessible = true }
    fun NetworkPayload.tags() = (tagsField.get(this) as Map<ResourceLocation, IntList>).toMutableMap()
    fun Map<ResourceLocation, IntList>.networkPayload() = payloadConstructor.newInstance(this) as NetworkPayload

}
