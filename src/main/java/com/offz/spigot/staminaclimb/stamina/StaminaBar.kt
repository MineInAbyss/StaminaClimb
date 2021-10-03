package com.offz.spigot.staminaclimb.stamina

import com.mineinabyss.idofront.messaging.color
import com.offz.spigot.staminaclimb.*
import com.offz.spigot.staminaclimb.climbing.ClimbBehaviour
import com.offz.spigot.staminaclimb.config.StaminaConfig
import org.bukkit.Bukkit
import org.bukkit.GameMode.ADVENTURE
import org.bukkit.GameMode.SURVIVAL
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerGameModeChangeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*
import kotlin.math.pow

object StaminaBar : Listener {
    var disabledPlayers: MutableList<UUID> = mutableListOf() //TODO persist
    var registeredBars: MutableMap<UUID, BossBar> = mutableMapOf()
    private var velocities: MutableMap<UUID, Double> = mutableMapOf()

    fun registerBar(player: Player): BossBar {
        val uuid = player.uniqueId
        uuid.restartCooldown()
        uuid.canClimb = true
        disabledPlayers.remove(uuid)
        val bossBar = Bukkit.createBossBar("&lStamina".color(), BarColor.GREEN, BarStyle.SEGMENTED_10)
        bossBar.addPlayer(player)
        registeredBars[uuid] = bossBar
        return bossBar
    }

    @EventHandler
    fun PlayerJoinEvent.onPlayerJoin() {
        registerBar(player)
    }

    @EventHandler
    fun PlayerQuitEvent.onPlayerQuit() {
        val player = player
        val uuid = player.uniqueId
        unregisterBar(uuid)
        if (ClimbBehaviour.isClimbing.containsKey(uuid)) ClimbBehaviour.stopClimbing(player)
    }

    @EventHandler
    fun PlayerMoveEvent.onPlayerMove() {
        val uuid = player.uniqueId
        val onLadder: Boolean = (player.location.block.type == Material.LADDER)
        if (!player.climbEnabled) return  //Only run if player has system turned on
        val vel = player.velocity.y
        if (vel < -0.1) {
            velocities[uuid] = vel
        }
        val loc = from
        val to = to ?: return
        val blockBelow: Location = player.location.subtract(0.0, 1.0, 0.0)
        val blockBelowBelow: Location = player.location.subtract(0.0, 2.0, 0.0)
        val blockAbove: Location = player.location.add(0.0, 1.0, 0.0)
        val blockAboveAbove: Location = player.location.add(0.0, 2.0, 0.0)
        val ladderBelowBelow: Boolean = (blockBelowBelow.block.type == Material.LADDER)
        val ladderData = player.location.block.blockData


        if (onLadder && !uuid.canClimb && ladderBelowBelow) {
            player.sendBlockChange(player.location, Material.AIR.createBlockData())
            player.sendBlockChange(blockBelow, Material.AIR.createBlockData())
            player.sendBlockChange(blockBelowBelow, Material.AIR.createBlockData())
            player.sendBlockChange(blockAbove, ladderData)
            player.sendBlockChange(blockAboveAbove, ladderData)
        }

        if (onLadder && uuid.canClimb && loc.distanceSquared(to) > 0.007) {
            uuid.removeProgress(StaminaConfig.data.staminaRemoveWhileOnLadder)
        }

        if (!onLadder && uuid.isClimbing && loc.distanceSquared(to) > 0.007)
            uuid.removeProgress(StaminaConfig.data.staminaRemoveWhileMoving)
    }

    @EventHandler
    fun EntityDamageEvent.onPlayerFall() { //Remove stamina from player falls
        val player = entity as? Player ?: return
        val uuid = player.uniqueId
        if (cause != EntityDamageEvent.DamageCause.FALL || !player.climbEnabled || !velocities.containsKey(uuid)) return

        val bossBar = registeredBars[uuid] ?: return
        val threshold = 0.6 //TODO make config and not dumb calculations
        val multiplier = 11.0
        val exponent = 1.1
        val vel = velocities[uuid] ?: return //TODO put this damage system into bonehurtingjuice
        bossBar.isVisible = true
        if (vel > -threshold) {
            bossBar.removeProgress(0.1 / 15)
            return
        }
        val damaged = ((vel + threshold) * -multiplier).pow(exponent)
        damage = damaged
        bossBar.removeProgress(damage / 15) //taking 15 damage reduces stamina fully
    }

    @EventHandler
    fun PlayerDeathEvent.onPlayerDeath() {
        val player = entity
        val uuid = player.uniqueId
        if (!player.climbEnabled) return
        registeredBars[uuid]?.progress = 1.0
    }

    @EventHandler
    fun PlayerGameModeChangeEvent.onGamemodeChange() {
        if (player.climbEnabled
            && (newGameMode == SURVIVAL || newGameMode == ADVENTURE)
            && !registeredBars.containsKey(player.uniqueId)
        ) {
            registerBar(player)
        }
    }

    fun unregisterBar(uuid: UUID) {
        ClimbBehaviour.cooldown.remove(uuid)
        registeredBars[uuid]?.removeAll()
        registeredBars.remove(uuid)
    }

    /** Removes [amount] progress from [bossBar]'s progress */
    internal fun removeProgress(amount: Double, bossBar: BossBar) {
        bossBar.progress = (bossBar.progress - amount).coerceIn(0.0..1.0)
    }

    /** Removes [amount] progress from [uuid]'s associated BossBar's progress */
    internal fun removeProgress(amount: Double, uuid: UUID) {
        removeProgress(amount, registeredBars[uuid] ?: return)
    }
}
