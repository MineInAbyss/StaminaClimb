package com.mineinabyss.staminaclimb.rope

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.mineinabyss.idofront.nms.aliases.toNMS
import com.mineinabyss.staminaclimb.canClimb
import com.mineinabyss.staminaclimb.checkRange
import com.mineinabyss.staminaclimb.climbing.ClimbBehaviour
import com.mineinabyss.staminaclimb.collidesAt
import com.mineinabyss.staminaclimb.fallSpeed
import com.mineinabyss.staminaclimb.isClimbing
import com.mineinabyss.staminaclimb.modules.ropeConfig
import com.mineinabyss.staminaclimb.modules.stamina
import com.mineinabyss.staminaclimb.normalizeOrZero
import com.mineinabyss.staminaclimb.standingHalfWidth
import com.mineinabyss.staminaclimb.standingHeight
import com.mineinabyss.staminaclimb.stopClimbing
import com.mineinabyss.staminaclimb.toUuidOrNull
import com.mineinabyss.staminaclimb.wallDifficultyAt
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector
import java.util.*

object RopeClimb {
    // How far from the rope a player can be moved onto a wall they grab from it
    private const val WALL_REACH = 0.5

    // Vanilla default of the block interaction range attribute
    private const val DEFAULT_BLOCK_REACH = 4.5

    // Any slower sideways speed on grabbing is walking drift rather than a swing worth carrying onto the rope
    private const val MIN_CARRIED_SPEED = 0.05

    // A climber on a mirror copy waits for its source chunk to load, then for the mirror to spawn a couple of ticks later
    private const val REJOIN_WAIT_TICKS = RopeMirrors.ENTITY_WAIT_TICKS + 5
    private val REJOIN_PITON = NamespacedKey("climb", "rope_piton")
    private val REJOIN_MIRROR = NamespacedKey("climb", "rope_mirror")

    private val HORIZONTAL = listOf(Vector(1, 0, 0), Vector(-1, 0, 0), Vector(0, 0, 1), Vector(0, 0, -1))

    private val byPlayer = HashMap<UUID, Climber>()
    private val playerByMount = HashMap<UUID, UUID>()

    private val onPiton = HashMap<UUID, MutableList<Climber>>()

    /** The lowest climber on each piton, whose swing the rope display tilts with and everyone else hangs along */
    private val leads = HashMap<UUID, Climber>()
    private val consumedSwings = HashMap<UUID, Int>()
    private var crossing: UUID? = null
    private val pendingRejoins = HashSet<UUID>()
    private val crossingChunks = HashSet<ChunkPos>()

    val climbers: Collection<Climber> get() = byPlayer.values
    val Player.isRopeClimbing get() = uniqueId in byPlayer
    fun climber(player: Player) = byPlayer[player.uniqueId]
    fun climberOfMount(mount: Entity) = playerByMount[mount.uniqueId]?.let(byPlayer::get)
    fun playersOn(piton: ItemDisplay) = onPiton[piton.uniqueId].orEmpty().mapNotNull { Bukkit.getPlayer(it.player) }
    fun countOn(piton: ItemDisplay) = onPiton[piton.uniqueId]?.size ?: 0

    fun leadOf(piton: ItemDisplay) = leads[piton.uniqueId]
    fun leadsRope(climber: Climber) = leadOf(climber.piton) === climber

    fun refreshLeads() = onPiton.keys.forEach(::refreshLead)

    /** A new lead takes over the swing at the same angular speed, so the rope carries on rather than jolting */
    private fun refreshLead(pitonId: UUID) {
        val lead = onPiton[pitonId]?.maxByOrNull { it.length } ?: return run { leads.remove(pitonId) }
        val previous = leads.put(pitonId, lead)
        if (previous == null || previous === lead) return
        lead.dir = previous.dir.clone()
        lead.vel = previous.vel.clone().multiply(lead.length / previous.length)
        previous.vel = Vector()
    }

    fun isSwinging(piton: ItemDisplay) = onPiton[piton.uniqueId]?.any { it.swinging } == true

    fun isCrossing(player: Player) = crossing == player.uniqueId

    /** So the wall-climb dash ignores this tick's arm swing */
    fun consumeSwing(player: Player) {
        consumedSwings[player.uniqueId] = Bukkit.getCurrentTick()
    }

    fun swingConsumed(player: Player) = consumedSwings[player.uniqueId]?.let { Bukkit.getCurrentTick() - it <= 1 } == true

    fun forget(player: Player) {
        consumedSwings.remove(player.uniqueId)
    }

    /** Double tapping sprint kicks off, so a climber wedged against a wall with nowhere to swing can get free */
    fun kick(player: Player) {
        val climber = byPlayer[player.uniqueId] ?: return
        val direction = kickDirection(player, climber).takeIf { it.lengthSquared() > 0.0 } ?: return
        climber.vel.add(direction.multiply(ropeConfig.swing.kickStrength))
        ropeConfig.sounds.kickWall.play(player.location)
    }

    private fun kickDirection(player: Player, climber: Climber): Vector {
        val feet = climber.feet
        val probe = ropeConfig.swing.kickProbe
        val away = Vector()
        HORIZONTAL.forEach { if (player.collidesAt(feet.clone().add(it.clone().multiply(probe)))) away.subtract(it) }
        if (away.lengthSquared() > 1e-6) return away.normalize()
        if (climber.placed.face.modY == 0) return climber.placed.face.direction
        return player.location.direction.setY(0).normalizeOrZero()
    }

    fun canGrab(player: Player) = player.fallSpeed == 0.0 || player.uniqueId.canClimb

    /** [link] leads to the copy of the rope grabbed, null for the piton's own */
    fun enter(player: Player, piton: ItemDisplay, feetY: Double, link: SectionLink? = null) {
        if (player.isRopeClimbing || !canGrab(player)) return
        val placed = piton.placedPiton ?: return
        val rope = piton.hangingRope ?: return
        val velocity = player.velocity
        val slideSpeed = player.fallSpeed
        if (player.uniqueId in ClimbBehaviour.isClimbing) player.stopClimbing()

        val anchor = placed.ropeAnchor(piton.world, link)
        val maxLength = rope.length.toDouble()
        val minLength = minOf(player.standingHeight, maxLength)
        val length = (anchor.y - feetY).coerceIn(minLength, maxLength)
        val mount = anchor.world.spawn(anchor.clone().subtract(0.0, length, 0.0), ItemDisplay::class.java) {
            PitonParts.markPart(it)
            it.teleportDuration = 1
        }

        val climber = Climber(player.uniqueId, piton, placed, mount, riderOffset(player, mount), wallClearance(player, placed), link, minLength, maxLength, length).apply {
            input = player.currentInput
            this.slideSpeed = slideSpeed
            Vector(velocity.x, 0.0, velocity.z).takeIf { it.lengthSquared() > MIN_CARRIED_SPEED * MIN_CARRIED_SPEED }?.let { vel = it }
        }

        climber.moveMount(player, climber.feet)
        byPlayer[player.uniqueId] = climber
        playerByMount[mount.uniqueId] = player.uniqueId
        onPiton.getOrPut(piton.uniqueId, ::mutableListOf).add(climber)
        refreshLead(piton.uniqueId)

        if (slideSpeed == 0.0) player.fallDistance = 0f
        mount.addPassenger(player)
        piton.pauseExpiry()
        PitonParts.hideRopeFrom(player, piton)
        ropeConfig.sounds.grabRope.play(player.location)
    }

    /** A rope hung closer to the wall than half the player's width would hold them inside it, so they hang out from the rope far enough to clear it */
    private fun wallClearance(player: Player, placed: PlacedPiton): Vector {
        if (placed.face.modY != 0) return Vector()
        val gap = player.standingHalfWidth - ropeConfig.ropeOffset
        return if (gap > 0) placed.face.direction.multiply(gap) else Vector()
    }

    /** The rider sits at the vehicle's passenger point minus its own vehicle attachment, 0.6 above the feet for a player */
    private fun riderOffset(player: Player, mount: ItemDisplay): Vector {
        val rider = player.toNMS()
        val vehicle = mount.toNMS()
        val seat = vehicle.getPassengerRidingPosition(rider).subtract(vehicle.position()).subtract(rider.getVehicleAttachmentPoint(vehicle))
        return Vector(seat.x, seat.y, seat.z)
    }

    fun exit(player: Player) = detach(player.uniqueId, player)

    fun rememberForRejoin(player: Player) {
        val climber = byPlayer[player.uniqueId] ?: return
        player.persistentDataContainer.set(REJOIN_PITON, PersistentDataType.STRING, climber.piton.uniqueId.toString())
        player.persistentDataContainer.set(REJOIN_MIRROR, PersistentDataType.BOOLEAN, climber.link != null)
    }

    /**
     * The entities of the chunk the player joins in can load a few ticks after them, so the piton is looked for until it shows up.
     * Gravity is off meanwhile so they are still at the rope when it does
     */
    fun rejoin(player: Player) {
        val pitonId = player.persistentDataContainer.get(REJOIN_PITON, PersistentDataType.STRING) ?: return
        val onMirror = player.persistentDataContainer.get(REJOIN_MIRROR, PersistentDataType.BOOLEAN) == true
        player.persistentDataContainer.remove(REJOIN_PITON)
        player.persistentDataContainer.remove(REJOIN_MIRROR)
        val uuid = pitonId.toUuidOrNull() ?: return

        val feetY = player.location.y
        player.setGravity(false)
        pendingRejoins += player.uniqueId
        stamina.plugin.launch {
            try {
                repeat(REJOIN_WAIT_TICKS) {
                    delay(1.ticks)
                    if (!player.isOnline) return@launch
                    val piton = Bukkit.getEntity(uuid) as? ItemDisplay ?: return@repeat
                    val link = if (onMirror) PitonParts.mirrorLinkOf(piton) ?: return@repeat else null
                    // Moved while offline, such as by a spawn plugin, so the rope is no longer theirs to hang on
                    if (piton.isWithinRejoinReach(player, link)) enter(player, piton, feetY, link)
                    return@launch
                }
            } finally {
                if (pendingRejoins.remove(player.uniqueId)) player.setGravity(true)
            }
        }
    }

    /** A player quitting mid wait would otherwise be saved without gravity, and their next join has no rejoin left to restore it */
    fun cancelRejoin(player: Player) {
        if (pendingRejoins.remove(player.uniqueId)) player.setGravity(true)
    }

    private fun ItemDisplay.isWithinRejoinReach(player: Player, link: SectionLink?): Boolean {
        val placed = placedPiton ?: return false
        val rope = hangingRope ?: return false
        val anchor = placed.ropeAnchor(world, link)
        return anchor.world == player.world && player.location.distance(anchor) <= rope.length + 1
    }

    /**
     * DeeperWorld never moves a climber riding the mount, so this does it at the same transition zones.
     * A zone maps onto the end of the overlap away from the zone leading back, so a climber resting on the line does not bounce between copies
     */
    fun crossSections(player: Player, climber: Climber) {
        val feet = climber.feet.toLocation(climber.world).takeIf(RopeSections::inTransition) ?: return
        val link = RopeSections.linkAt(feet)?.takeIf { it.world == player.world } ?: return
        val current = climber.link
        // A mirror climber only ever crosses back onto the piton's own copy
        if (current != null && link.target != current.source) return

        val to = link.toTarget(feet)
        // Nobody may be near the other copy yet, so its chunk is loaded first and the crossing waits for a later tick
        if (!to.isChunkLoaded) {
            val chunk = to.chunkPos
            if (crossingChunks.add(chunk)) to.world.getChunkAtAsync(to).thenRun { crossingChunks -= chunk }
            return
        }
        if (player.collidesAt(to.toVector())) return

        // A slipping grab's fall carries on across, a gripped climber's stays at zero
        val fallDistance = player.fallDistance
        crossing = player.uniqueId
        // The mount's one tick of position smoothing would sweep the 16k block jump across the client's screen
        climber.mount.teleportDuration = 0
        val moved = try {
            climber.mount.teleport(link.toTarget(climber.mount.location), PlayerTeleportEvent.TeleportCause.PLUGIN)
        } finally {
            crossing = null
        }

        stamina.plugin.launch {
            delay(2.ticks)
            if (climber.mount.isValid) climber.mount.teleportDuration = 1
        }

        if (!moved) return
        climber.link = if (current == null) link else null
        player.fallDistance = fallDistance
    }

    /** [player] is null when they went offline still hanging, the mount and the piton get cleaned up either way */
    fun detach(uuid: UUID, player: Player?) {
        val climber = byPlayer.remove(uuid) ?: return
        playerByMount.remove(climber.mount.uniqueId)
        // The dismount listener cancels a cancellable dismount, so the climber has to be gone from the map before this
        player?.leaveVehicle()
        climber.mount.remove()

        PitonParts.showRopeTo(uuid, climber.piton)
        // A grab that is still slipping is a fall in progress, its distance stays with the player
        if (player != null && climber.slideSpeed == 0.0) player.fallDistance = 0f

        val pitonId = climber.piton.uniqueId
        val others = onPiton[pitonId]
        others?.remove(climber)
        if (others.isNullOrEmpty()) onPiton.remove(pitonId)
        refreshLead(pitonId)

        if (climber.piton.isDead) return
        val lead = leads[pitonId]
        if (lead == null) climber.piton.restartExpiry()
        PitonParts.setRopeSwing(climber.piton, lead?.swingDir)
    }

    /** The player is moved as close to the wall as a walking player gets, since the wall check only probes [checkRange] out */
    fun climbWall(player: Player, block: Block, face: BlockFace): Boolean {
        val climber = byPlayer[player.uniqueId] ?: return false
        if (!ClimbBehaviour.mayClimbWall(player)) return false
        val halfWidth = player.standingHalfWidth

        val candidates = buildList {
            if (face.modY == 0) {
                val reach = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE)?.value ?: DEFAULT_BLOCK_REACH
                val hit = player.rayTraceBlocks(reach)?.takeIf { it.hitBlock == block && it.hitBlockFace == face }?.hitPosition
                    ?: block.location.toCenterLocation().toVector().add(face.direction.multiply(0.5))
                add(player.location.apply {
                    x = hit.x + face.modX * halfWidth
                    z = hit.z + face.modZ * halfWidth
                })
            }
            val wall = climber.placed.face
            if (wall != BlockFace.DOWN) add(player.location.apply {
                val nudge = (ropeConfig.ropeOffset - halfWidth).coerceAtLeast(0.0)
                x = climber.anchor.x - wall.modX * nudge
                z = climber.anchor.z - wall.modZ * nudge
            })
            add(player.location)
        }

        val feet = climber.feet
        val standAt = candidates.firstOrNull {
            it.toVector().setY(feet.y).distanceSquared(feet) <= WALL_REACH * WALL_REACH && !player.collidesAt(it.toVector()) && player.wallDifficultyAt(it) >= 0
        } ?: return false

        exit(player)
        player.teleport(standAt)
        ClimbBehaviour.startClimbing(player)
        return true
    }

    fun release(player: Player) {
        val climber = byPlayer[player.uniqueId] ?: return
        val momentum = climber.vel.clone()
        val swinging = climber.swinging
        exit(player)
        if (swinging && momentum.length() >= ropeConfig.swing.releaseDashBelow) {
            player.velocity = momentum
            return
        }

        if (!ClimbBehaviour.mayClimbWall(player)) return
        // Registered but not climbing, the same state a wall dash leaves behind so a second air dash still works
        player.uniqueId.isClimbing = false
        ClimbBehaviour.dash(player)
    }

    /** Players are still online while plugins disable on shutdown, so their quit never gets to remember the rope */
    fun exitAll() {
        byPlayer.keys.toList().forEach { uuid ->
            val player = Bukkit.getPlayer(uuid)?.also(::rememberForRejoin)
            detach(uuid, player)
        }
        consumedSwings.clear()
    }
}
