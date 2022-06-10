package com.mineinabyss.staminaclimb.stamina

import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.idofront.entities.toPlayer
import com.mineinabyss.idofront.messaging.miniMsg
import com.mineinabyss.staminaclimb.*
import com.mineinabyss.staminaclimb.climbing.ClimbBehaviour
import com.mineinabyss.staminaclimb.config.StaminaConfig
import com.mineinabyss.staminaclimb.nms.Tags
import com.mineinabyss.staminaclimb.nms.Tags.createPayload
import kotlinx.coroutines.delay
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.bossbar.BossBar.Overlay
import net.minecraft.core.Registry
import net.minecraft.network.protocol.game.ClientboundUpdateTagsPacket
import org.bukkit.Bukkit
import org.bukkit.GameMode.ADVENTURE
import org.bukkit.GameMode.SURVIVAL
import org.bukkit.Location
import org.bukkit.Tag
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftPlayer
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
    var disabledPlayers: MutableList<UUID> = mutableListOf() //TODO persist
    var registeredBars: MutableMap<UUID, BossBar> = mutableMapOf()
    private var velocities: MutableMap<UUID, Double> = mutableMapOf()
    var fallDist: MutableMap<UUID, Location> = mutableMapOf()

    fun registerBar(player: Player): BossBar {
        val uuid = player.uniqueId
        uuid.restartCooldown()
        uuid.canClimb = true
        disabledPlayers.remove(uuid)
        val bossBar = BossBar.bossBar(
            "<b>Stamina".miniMsg(),
            1f,
            BossBar.Color.GREEN,
            Overlay.NOTCHED_10
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
        val packet = ClientboundUpdateTagsPacket(mapOf(Registry.BLOCK_REGISTRY to createPayload(map)))
        (player as CraftPlayer).handle.connection.send(packet)
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
        val climbDisabled = Tags.disabledPlayers.contains(player)
        val vel = player.velocity.y
        if (vel < -0.1) {
            velocities[uuid] = vel
        }

        if (onClimbable && !player.climbEnabled) {
            if (!climbDisabled) {
                fallDist[uuid] = player.location
                Tags.disableClimb(player)
                applyClimbDamage(player)
            }
        }

        if (onClimbable && !uuid.canClimb) {
            if (!climbDisabled) {
                fallDist[uuid] = player.location
                Tags.disableClimb(player)
                staminaClimb.launch {
                    while (!uuid.canClimb) {
                        delay(1.seconds)
                    }
                    Tags.enableClimb(player)
                }
                applyClimbDamage(player)
            }
        }

        if (onClimbable && uuid.canClimb && from.distanceSquared(to) > 0.007)
            uuid.toPlayer()?.addStamina(-StaminaConfig.data.staminaRemoveWhileOnLadder)

        if (!onClimbable && uuid.isClimbing && from.distanceSquared(to) > 0.007)
            uuid.toPlayer()?.addStamina(-StaminaConfig.data.staminaRemoveWhileMoving)
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
            bossBar.addProgress(-0.1f / 15f)
            return
        }
        val damaged = ((vel + threshold) * -multiplier).pow(exponent)
        damage = damaged
        bossBar.addProgress(-damage.toFloat() / 15f) //taking 15 damage reduces stamina fully
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
}

/** Removes [amount] progress from [bossBar]'s progress */
internal fun BossBar.addProgress(amount: Float) {
    progress((progress() + amount).coerceIn(0.0f..1.0f))
}

/** Removes [amount] progress from [uuid]'s associated BossBar's progress */
internal fun Player.addStamina(amount: Float) {
    StaminaBar.registeredBars[uniqueId]?.addProgress(amount)
}
