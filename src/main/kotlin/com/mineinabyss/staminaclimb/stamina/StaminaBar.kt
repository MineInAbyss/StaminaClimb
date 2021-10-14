package com.mineinabyss.staminaclimb.stamina

import com.mineinabyss.idofront.messaging.broadcastVal
import com.mineinabyss.idofront.messaging.color
import com.mineinabyss.staminaclimb.*
import com.mineinabyss.staminaclimb.climbing.ClimbBehaviour
import com.mineinabyss.staminaclimb.config.StaminaConfig
import com.mineinabyss.staminaclimb.nms.Tags
import com.okkero.skedule.schedule
import org.bukkit.Bukkit
import org.bukkit.GameMode.ADVENTURE
import org.bukkit.GameMode.SURVIVAL
import org.bukkit.Tag
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
    private var fallDist: MutableMap<UUID, Int> = mutableMapOf()

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
        val onClimbable: Boolean = Tag.CLIMBABLE.isTagged(player.location.block.type)
        if (!player.climbEnabled) return  //Only run if player has system turned on
        val vel = player.velocity.y
        if (vel < -0.1) {
            velocities[uuid] = vel
        }

        if (onClimbable && !uuid.canClimb) {
            fallDist[uuid] = fallDist[uuid]?.plus(1) ?: 1
            fallDist[uuid].broadcastVal("dist: ") //
            if (!Tags.disabledPlayers.contains(player)) {
                Tags.disableClimb(player)
                staminaClimb.schedule {
                    while (!uuid.canClimb) {
                        waitFor(20)
                    }
                    Tags.enableClimb(player)
                }
                staminaClimb.schedule {
                    while (!player.location.apply { y -= 1 }.block.isSolid) {
                        waitFor(1)
                    }
                    // player.hurtBones(fallDist[uuid]) // depend on bonehurtjuice
                    player.fallDistance = fallDist[uuid]?.toFloat() ?: 1.0f
                    fallDist.remove(uuid)
                }
            }
        }

        if (onClimbable && uuid.canClimb && from.distanceSquared(to) > 0.007) {
            uuid.removeProgress(StaminaConfig.data.staminaRemoveWhileOnLadder)
        }

        if (!onClimbable && uuid.isClimbing && from.distanceSquared(to) > 0.007)
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
