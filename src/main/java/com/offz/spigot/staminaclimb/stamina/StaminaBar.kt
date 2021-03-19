package com.offz.spigot.staminaclimb.stamina

import com.mineinabyss.idofront.messaging.color
import com.offz.spigot.staminaclimb.*
import com.offz.spigot.staminaclimb.climbing.ClimbBehaviour
import com.offz.spigot.staminaclimb.config.StaminaConfig
import org.bukkit.Bukkit
import org.bukkit.GameMode.ADVENTURE
import org.bukkit.GameMode.SURVIVAL
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
    var velocities: MutableMap<UUID, Double> = mutableMapOf()
    private var ySpeeds: MutableMap<UUID, Double> = mutableMapOf()

    private var previousTickTime: Long = 0
    private val targetDeltaTime: Double = 1.0 / 20.0 //20 ticks per second

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
            ySpeeds[uuid] = vel
        }

        val loc = e.from
        val to = e.to ?: return
        velocities[uuid] = loc.distanceSquared(to)
    }

    @EventHandler
    fun onPlayerFall(e: EntityDamageEvent) { //Remove stamina from player falls
        val player = e.entity as? Player ?: return
        val uuid = player.uniqueId
        if (e.cause != EntityDamageEvent.DamageCause.FALL || !player.climbEnabled || !ySpeeds.containsKey(uuid)) return

        val bossBar = registeredBars[uuid] ?: return
        val threshold = 0.6 //TODO make config and not dumb calculations
        val multiplier = 11.0
        val exponent = 1.1
        val vel = ySpeeds[uuid] ?: return //TODO put this damage system into bonehurtingjuice
        bossBar.isVisible = true
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
        registeredBars[uuid]?.progress = 1.0
    }

    @EventHandler
    fun onGamemodeChange(e: PlayerGameModeChangeEvent) {
        if (e.player.climbEnabled
            && (e.newGameMode == SURVIVAL || e.newGameMode == ADVENTURE)
            && !registeredBars.containsKey(e.player.uniqueId)
        ) {
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
        bossBar.progress = (bossBar.progress - amount).coerceIn(0.0..1.0)
    }

    /** Removes [amount] progress from [uuid]'s associated BossBar's progress */
    internal fun removeProgress(amount: Double, uuid: UUID) {
        removeProgress(amount, registeredBars[uuid] ?: return)
    }

    /** Removes [amount] progress from [uuid]'s associated BossBar's progress, accounting for deltatime */
    public fun removeProgressWithDeltaTime(amount: Double, uuid: UUID) {
        val deltaTime = (System.currentTimeMillis() - previousTickTime) / 1000.0;
        val timeMultiplier = if(previousTickTime != 0L) deltaTime / targetDeltaTime else 1.0;
        previousTickTime = System.currentTimeMillis();
        removeProgress(amount * timeMultiplier, uuid)
    }

    /** Adds [amount] progress to [bossBar]'s progress */
    internal fun addProgress(amount: Double, bossBar: BossBar) {
        bossBar.progress = (bossBar.progress + amount).coerceIn(0.0..1.0)
    }

    /** Adds [amount] progress to [uuid]'s associated BossBar's progress */
    internal fun addProgress(amount: Double, uuid: UUID) {
        addProgress(amount, registeredBars[uuid] ?: return)
    }

    /** Ads [amount] progress to [uuid]'s associated BossBar's progress, accounting for deltatime */
    public fun addProgressWithDeltaTime(amount: Double, uuid: UUID) {
        val deltaTime = (System.currentTimeMillis() - previousTickTime) / 1000.0;
        val timeMultiplier = if(previousTickTime != 0L) deltaTime / targetDeltaTime else 1.0;
        previousTickTime = System.currentTimeMillis();
        addProgress(amount * timeMultiplier, uuid)
    }
}
