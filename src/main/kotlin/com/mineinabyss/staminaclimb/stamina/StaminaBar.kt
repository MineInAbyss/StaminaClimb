package com.mineinabyss.staminaclimb.stamina

import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.idofront.entities.toPlayer
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.staminaclimb.*
import com.mineinabyss.staminaclimb.climbing.ClimbBehaviour
import com.mineinabyss.staminaclimb.modules.stamina
import com.mineinabyss.staminaclimb.nms.Tags
import com.mineinabyss.staminaclimb.nms.Tags.createPayload
import kotlinx.coroutines.delay
import net.kyori.adventure.bossbar.BossBar
import net.minecraft.core.registries.Registries
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket
import org.bukkit.Bukkit
import org.bukkit.GameMode.ADVENTURE
import org.bukkit.GameMode.SURVIVAL
import org.bukkit.Location
import org.bukkit.craftbukkit.v1_20_R2.entity.CraftPlayer
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
import kotlin.time.Duration.Companion.seconds

object StaminaBar : Listener {
    private val conf = stamina.config
    private val disabledPlayers: MutableList<UUID> = mutableListOf() //TODO persist

    @PublishedApi
    internal val registeredBars: MutableMap<UUID, BossBar> = mutableMapOf()
    private val velocities: MutableMap<UUID, Double> = mutableMapOf()
    private val fallDist: MutableMap<UUID, Location> = mutableMapOf()
    private val barProgressTracker = mutableMapOf<UUID, Float>()

    fun climbEnabled(player: Player) = player.uniqueId !in disabledPlayers

    fun setClimbEnabled(player: Player, enable: Boolean) {
        if (climbEnabled(player) == enable) return
        val uuid = player.uniqueId
        if (enable) {
            disabledPlayers.remove(uuid)
            registerBar(player).progress(0f)
        } else {
            disabledPlayers.add(uuid)
            unregisterBar(uuid)
            player.stopClimbing()
        }
    }

    fun registerBar(player: Player): BossBar {
        val uuid = player.uniqueId
        uuid.restartCooldown()
        uuid.canClimb = true
        disabledPlayers.remove(uuid)
        val bossBar = BossBar.bossBar(
            "<b>Stamina".miniMsg(),
            1f,
            conf.baseBarColor,
            conf.baseOverlay
        )
        bossBar.addListener(object : BossBar.Listener {
            override fun bossBarProgressChanged(bar: BossBar, oldProgress: Float, newProgress: Float) {
                //Hide when full
                if (newProgress < 1f)
                    player.showBossBar(bar)
                else player.hideBossBar(bar)

            }
        })
        registeredBars[uuid] = bossBar
        return bossBar
    }

    @EventHandler
    fun PlayerJoinEvent.onPlayerJoin() {
        val map = Tags.emptyFallDamageResetTag(player)
        val packet = ClientboundUpdateTagsPacket(mapOf(Registries.BLOCK to createPayload(map)))
        (player as CraftPlayer).handle.connection.send(packet)
        registerBar(player)
        if (player.isClimbing && player.uniqueId in barProgressTracker) {
            player.setStamina(barProgressTracker[player.uniqueId]!!)
            barProgressTracker.remove(player.uniqueId)
        } else if (player.isClimbing && player.uniqueId !in barProgressTracker) {
            ClimbBehaviour.stopClimbing(player)
        }
    }

    @EventHandler
    fun PlayerQuitEvent.onPlayerQuit() {
        val player = player
        val uuid = player.uniqueId
        if (uuid in registeredBars)
            barProgressTracker[uuid] = registeredBars[uuid]?.progress() ?: 0f
        unregisterBar(uuid)
        if (ClimbBehaviour.isClimbing.containsKey(uuid)) ClimbBehaviour.stopClimbing(player)
    }

    @EventHandler
    fun PlayerMoveEvent.onPlayerMove() {
        val uuid = player.uniqueId
        val climbDisabled = Tags.disabledPlayers.contains(player)
        val vel = player.velocity.y
        if (vel < -0.1) {
            velocities[uuid] = vel
        }

        if (player.isClimbing && !player.climbEnabled) {
            if (!climbDisabled) {
                fallDist[uuid] = player.location
                Tags.disableClimb(player)
                applyClimbDamage(player)
            }
        }

        if (player.isClimbing && !uuid.canClimb) {
            if (!climbDisabled) {
                fallDist[uuid] = player.location
                Tags.disableClimb(player)
                stamina.plugin.launch {
                    while (!uuid.canClimb) {
                        delay(1.seconds)
                    }
                    Tags.enableClimb(player)
                }
                applyClimbDamage(player)
            }
        }

        if (player.isClimbing && uuid.canClimb && from.distanceSquared(to) > 0.007)
            player.removeStamina(conf.staminaRemoveWhileOnLadder)

        if (!player.isClimbing && uuid.isClimbing && from.distanceSquared(to) > 0.007)
            player.removeStamina(conf.staminaRemoveWhileMoving)
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
        player.hideBossBar(bossBar)
        if (vel > -threshold) {
            bossBar.removeProgress(0.1f / 15f)
            return
        }
        val damaged = ((vel + threshold) * -multiplier).pow(exponent)
        damage = damaged
        bossBar.removeProgress(damage.toFloat() / 15f) //taking 15 damage reduces stamina fully
    }

    @EventHandler
    fun PlayerDeathEvent.onPlayerDeath() {
        val player = entity
        val uuid = player.uniqueId
        if (!player.climbEnabled) return
        registeredBars[uuid]?.progress(1.0f)
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
        val bar = registeredBars[uuid] ?: return
        Bukkit.getServer().hideBossBar(bar)
        registeredBars.remove(uuid)
    }

    inline fun forEachBar(run: (Player, UUID, BossBar) -> Unit) {
        registeredBars.toMap().forEach { (uuid, bar) ->
            val player = uuid.toPlayer() ?: registeredBars.remove(uuid).let { return@forEach }
            run(player, uuid, bar)
        }
    }

    fun applyClimbDamage(player: Player) {
        stamina.plugin.launch {
            while (!player.location.apply { y -= 1 }.block.isSolid) {
                delay(1)
            }
//        if (Plugins.isEnabled()) {
//            if (StaminaBar.fallDist.containsKey(player.uniqueId)) {
//                player.hurtBones((StaminaBar.fallDist[player.uniqueId]!!.y - player.location.y).toFloat())
//            }
//        }
            fallDist.remove(player.uniqueId)
        }
    }

}

/** Adds [amount] progress from [bossBar]'s progress */
internal fun BossBar.addProgress(amount: Float) {
    progress((progress() + amount).coerceIn(0.0f..1.0f))
}

/** Removes [amount] progress from [bossBar]'s progress */
internal fun BossBar.removeProgress(amount: Float) {
    progress((progress() - amount).coerceIn(0.0f..1.0f))
}

/** Sets progress to [amount] from [bossBar]'s progress */
internal fun BossBar.setProgress(amount: Float) {
    progress((amount).coerceIn(0.0f..1.0f))
}

/** Adds [amount] progress from [uuid]'s associated BossBar's progress */
internal fun Player.addStamina(amount: Float) {
    StaminaBar.registeredBars[uniqueId]?.addProgress(amount)
}

/** Removes [amount] progress from [uuid]'s associated BossBar's progress */
internal fun Player.removeStamina(amount: Float) {
    StaminaBar.registeredBars[uniqueId]?.removeProgress(amount)
}

/** Sets progress to [amount] from [uuid]'s associated BossBar's progress */
internal fun Player.setStamina(amount: Float) {
    StaminaBar.registeredBars[uniqueId]?.setProgress(amount)
}
