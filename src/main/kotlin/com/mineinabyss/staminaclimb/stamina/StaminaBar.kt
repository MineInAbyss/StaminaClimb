package com.mineinabyss.staminaclimb.stamina

import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.idofront.entities.toPlayer
import com.mineinabyss.staminaclimb.*
import com.mineinabyss.staminaclimb.climbing.ClimbBehaviour
import com.mineinabyss.staminaclimb.modules.stamina
import com.mineinabyss.staminaclimb.nms.Tags
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.GameMode.ADVENTURE
import org.bukkit.GameMode.SURVIVAL
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerGameModeChangeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow
import kotlin.time.Duration.Companion.seconds

object StaminaBar : Listener {
    internal var conf = stamina.config
    private val disabledPlayers: ObjectArrayList<UUID> = ObjectArrayList() //TODO persist

    @PublishedApi
    internal val players: MutableMap<UUID, PlayerStamina> = ConcurrentHashMap()

    internal var display: StaminaDisplay = BossBarDisplay
        private set

    // Checked instead of the display type, since MythicHudDisplay cannot be loaded without MythicHUD present
    private var usingMythicHud = false

    private val velocities: Object2ObjectOpenHashMap<UUID, Double> = Object2ObjectOpenHashMap()
    private val barProgressTracker: Object2ObjectOpenHashMap<UUID, Float> = Object2ObjectOpenHashMap()

    fun climbEnabled(player: Player) = player.uniqueId !in disabledPlayers

    fun setClimbEnabled(player: Player, enable: Boolean) {
        if (climbEnabled(player) == enable) return
        val uuid = player.uniqueId
        if (enable) {
            disabledPlayers.remove(uuid)
            registerBar(player)
            player.setStamina(0f)
        } else {
            disabledPlayers.add(uuid)
            unregisterBar(uuid)
            player.stopClimbing()
        }
    }

    fun registerBar(player: Player): PlayerStamina {
        val uuid = player.uniqueId
        uuid.restartCooldown()
        uuid.canClimb = true
        disabledPlayers.remove(uuid)
        val stamina = PlayerStamina()
        players[uuid] = stamina
        display.show(player, stamina)
        return stamina
    }

    fun selectDisplay() {
        val useMythicHud = conf.useMythicHud && Bukkit.getPluginManager().isPluginEnabled("MythicHUD")
        if (useMythicHud == usingMythicHud) return

        disableDisplay()
        display = if (useMythicHud) MythicHudDisplay.also { it.register() } else BossBarDisplay
        usingMythicHud = useMythicHud
        forEachPlayer { player, _, stamina -> display.show(player, stamina) }
    }

    fun disableDisplay() {
        display.hideAll()
        if (usingMythicHud) MythicHudDisplay.unregister()
    }

    internal fun update(player: Player, stamina: PlayerStamina, progress: Float = stamina.progress, alert: Boolean = stamina.alert) {
        val clamped = progress.coerceIn(0.0f..1.0f)
        if (clamped == stamina.progress && alert == stamina.alert) return
        stamina.progress = clamped
        stamina.alert = alert
        display.update(player, stamina)
    }

    @EventHandler
    fun PlayerJoinEvent.onPlayerJoin() {
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
        if (uuid in players)
            barProgressTracker[uuid] = players[uuid]?.progress ?: 0f
        unregisterBar(uuid)
        if (ClimbBehaviour.isClimbing.containsKey(uuid)) ClimbBehaviour.stopClimbing(player)
    }

    @EventHandler
    fun PlayerMoveEvent.onPlayerMove() {
        val uuid = player.uniqueId
        val climbDisabled = player in Tags.disabledPlayers
        // The server never runs a player's physics, so their velocity is not how fast they fall
        val vel = to.y - from.y
        if (vel < -0.1) velocities[uuid] = vel

        if (player.isClimbing && !player.climbEnabled && !climbDisabled) Tags.disableClimb(player)

        if (player.isClimbing && !uuid.canClimb && !climbDisabled) {
            Tags.disableClimb(player)
            stamina.plugin.launch {
                while (!uuid.canClimb) delay(1.seconds)
                Tags.enableClimb(player)
            }
        }

        if (player.isClimbing && uuid.canClimb && from.distanceSquared(to) > 0.007)
            player.removeStamina(conf.staminaRemoveWhileOnClimbable)

        if (!player.isClimbing && uuid.isClimbing && from.distanceSquared(to) > 0.007)
            player.removeStamina(conf.staminaRemoveWhileMoving)
    }

    @EventHandler
    fun EntityDamageEvent.onPlayerFall() { //Remove stamina from player falls
        val player = entity as? Player ?: return
        val uuid = player.uniqueId
        if (cause != EntityDamageEvent.DamageCause.FALL || !player.climbEnabled || !velocities.containsKey(uuid)) return

        if (uuid !in players) return
        val threshold = 0.6
        val vel = velocities[uuid] ?: return
        if (vel > -threshold) player.removeStamina(0.1f / 15f)
        else player.removeStamina(damage.toFloat() / 15f) //taking 15 damage reduces stamina fully
    }

    @EventHandler
    fun PlayerDeathEvent.onPlayerDeath() {
        val player = entity
        val uuid = player.uniqueId
        if (!player.climbEnabled) return
        player.setStamina(1.0f)
    }

    @EventHandler
    fun PlayerGameModeChangeEvent.onGamemodeChange() {
        if (player.climbEnabled
            && (newGameMode == SURVIVAL || newGameMode == ADVENTURE)
            && !players.containsKey(player.uniqueId)
        ) {
            registerBar(player)
            player.setStamina(1f)
        }
    }

    fun unregisterBar(uuid: UUID) {
        ClimbBehaviour.cooldown.remove(uuid)
        players.remove(uuid) ?: return
        display.hide(uuid)
    }

    inline fun forEachPlayer(run: (Player, UUID, PlayerStamina) -> Unit) {
        players.toMap().forEach { (uuid, stamina) ->
            val player = uuid.toPlayer() ?: players.remove(uuid).let { return@forEach }
            run(player, uuid, stamina)
        }
    }

}

internal fun Player.addStamina(amount: Float) {
    val stamina = StaminaBar.players[uniqueId] ?: return
    StaminaBar.update(this, stamina, progress = stamina.progress + amount)
}

internal fun Player.removeStamina(amount: Float) = addStamina(-amount)

internal fun Player.setStamina(amount: Float) {
    val stamina = StaminaBar.players[uniqueId] ?: return
    StaminaBar.update(this, stamina, progress = amount)
}

val SLOWNESS_EFFECT = PotionEffect(PotionEffectType.SLOWNESS, 110, 2, false, false)
val WEAKNESS_EFFECT = PotionEffect(PotionEffectType.WEAKNESS, 110, 2, false, false)
