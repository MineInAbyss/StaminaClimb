package com.mineinabyss.staminaclimb.nms

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import net.minecraft.core.IRegistry
import net.minecraft.network.protocol.game.PacketPlayOutTags
import net.minecraft.resources.MinecraftKey
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.tags.Tags
import org.bukkit.Bukkit
import org.bukkit.craftbukkit.v1_17_R1.CraftServer
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftPlayer
import org.bukkit.entity.Player

object Tags {

    val disabledPlayers = mutableSetOf<Player>()

    fun enableClimb(player: Player) {
        if (!disabledPlayers.contains(player)) return
        disabledPlayers -= player

        val server: MinecraftServer = (Bukkit.getServer() as CraftServer).server
        val originalTags = server.tagRegistry.a(server.l) + mapOf<ResourceKey<out IRegistry<*>>, Tags.a>()

        val packet = PacketPlayOutTags(originalTags)
        (player as CraftPlayer).handle.b.sendPacket(packet)
    }

    fun disableClimb(player: Player) {
        if (disabledPlayers.contains(player)) return
        disabledPlayers += player

        val server: MinecraftServer = (Bukkit.getServer() as CraftServer).server
        val newTags = server.tagRegistry.a(server.l) + mapOf<ResourceKey<out IRegistry<*>>, Tags.a>()
        val blockTags = newTags.filterKeys { it.a().key == "block" }.values.first()

        val tags = (blockTags?.getPrivateProperty("a") as? Map<MinecraftKey, IntList>
            ?: error("unable to cast")).toMutableMap()

        tags[MinecraftKey("climbable")] = IntArrayList()

        blockTags.setAndReturnPrivateProperty("a", tags)

        val packet = PacketPlayOutTags(newTags)
        (player as CraftPlayer).handle.b.sendPacket(packet)
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