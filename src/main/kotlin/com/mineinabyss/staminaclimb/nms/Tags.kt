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

        val climbable = Registry.BLOCK.getTag(BlockTags.CLIMBABLE).get()
        val list: IntList = IntArrayList(climbable.size()).apply {
            climbable.forEach { add(Registry.BLOCK.getId(it.value())) }
        }
        val payload = createPayload(mapOf(BlockTags.CLIMBABLE.location to list))
        val packet = ClientboundUpdateTagsPacket(mapOf(Registry.BLOCK_REGISTRY to payload))
        (player as CraftPlayer).handle.connection.send(packet)
    }

    fun disableClimb(player: Player) {
        if (disabledPlayers.contains(player)) return
        disabledPlayers += player

        val payload = createPayload(mapOf(BlockTags.CLIMBABLE.location to IntArrayList()))
        val packet = ClientboundUpdateTagsPacket(mapOf(Registry.BLOCK_REGISTRY to payload))
        (player as CraftPlayer).handle.connection.send(packet)
    }


}

fun <T : Any> T.getPrivateProperty(variableName: String): Any? {
    return javaClass.getDeclaredField(variableName).let { field ->
        field.isAccessible = true
        return@let field.get(this)
    }
}

fun <T : Any> T.setAndReturnPrivateProperty(variableName: String, data: Any): Any? {
    return javaClass.getDeclaredField(variableName).let { field ->
        field.isAccessible = true
        field.set(this, data)
        return@let field.get(this)
    }
}
