package com.offz.spigot.staminaclimb.stamina

import com.mineinabyss.idofront.messaging.color
import com.offz.spigot.staminaclimb.*
import com.offz.spigot.staminaclimb.climbing.ClimbBehaviour
import org.bukkit.Bukkit
import org.bukkit.GameMode
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

    fun registerBar(player: Player) {
        val uuid = player.uniqueId
        uuid.restartCooldown()
        uuid.canClimb = true
        disabledPlayers.remove(uuid)
        val bossBar = Bukkit.createBossBar("&lStamina".color(), BarColor.GREEN, BarStyle.SEGMENTED_10)
        bossBar.addPlayer(player)
        registeredBars[uuid] = bossBar
    }

    @EventHandler
    fun onPlayerJoin(e: PlayerJoinEvent) {
        registerBar(e.player)
    }

    @EventHandler
    fun onPlayerQuit(e: PlayerQuitEvent) {
        val player = e.player
        val uuid = player.uniqueId
        unregisterBar(uuid)
        if (ClimbBehaviour.isClimbing.containsKey(uuid)) ClimbBehaviour.stopClimbing(player)
    }

    @EventHandler
    fun onPlayerMove(e: PlayerMoveEvent) {
        val player = e.player
        val uuid = player.uniqueId
        if (!player.climbEnabled) return  //Only run if player has system turned on
        val vel = player.velocity.y
        if (vel < -0.1) {
            velocities[uuid] = vel
        }
        val loc = e.from
        val to = e.to ?: return

        //if player is climbing and has moved
        if (uuid.isClimbing && loc.distanceSquared(to) > 0.007)
            uuid.removeProgress(climbyConfig.STAMINA_REMOVE_WHILE_MOVING)
    }

    @EventHandler
    fun onPlayerFall(e: EntityDamageEvent) { //Remove stamina from player falls
        val player = e.entity as? Player ?: return
        val uuid = player.uniqueId
        if (e.cause != EntityDamageEvent.DamageCause.FALL || !player.climbEnabled || !velocities.containsKey(uuid)) return

        val bossBar = registeredBars[uuid]
        val threshold = 0.6 //TODO make config and not dumb calculations
        val multiplier = 11.0
        val exponent = 1.1
        val vel = velocities[uuid]!!

        bossBar!!.isVisible = true
        if (vel > -threshold) {
            bossBar.removeProgress(0.1 / 15)
            return
        }
        val damage = ((vel + threshold) * -multiplier).pow(exponent)
        e.damage = damage
        bossBar.removeProgress(damage / 15) //taking 15 damage reduces stamina fully
    }

    @EventHandler
    fun onPlayerDeath(e: PlayerDeathEvent) {
        val player = e.entity
        val uuid = player.uniqueId
        if (!player.climbEnabled) return
        val bossBar = registeredBars[uuid]
        bossBar!!.progress = 1.0
    }

    @EventHandler
    fun onGamemodeChange(e: PlayerGameModeChangeEvent) {
        if (e.player.climbEnabled && (e.newGameMode == GameMode.SURVIVAL || e.newGameMode == GameMode.ADVENTURE)
                && !registeredBars.containsKey(e.player.uniqueId)) {
            registerBar(e.player)
        }
    }

    fun unregisterBar(uuid: UUID) {
        ClimbBehaviour.cooldown.remove(uuid)
        registeredBars[uuid]?.removeAll()
        registeredBars.remove(uuid)
    }

    /** Removes [amount] progress from [bossBar]'s progress */
    internal fun removeProgress(amount: Double, bossBar: BossBar) {
        bossBar.progress = (bossBar.progress - amount).coerceAtLeast(0.0)
    }

    /** Removes [amount] progress from [uuid]'s associated BossBar's progress */
    internal fun removeProgress(amount: Double, uuid: UUID) {
        removeProgress(amount, registeredBars[uuid] ?: return)
    }
}