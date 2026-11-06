package com.mineinabyss.staminaclimb.rope

import com.mineinabyss.idofront.nms.aliases.toNMS
import com.mineinabyss.staminaclimb.canClimb
import com.mineinabyss.staminaclimb.collidesAt
import com.mineinabyss.staminaclimb.hasCollision
import com.mineinabyss.staminaclimb.modules.ropeConfig
import com.mineinabyss.staminaclimb.normalizeOrZero
import com.mineinabyss.staminaclimb.stamina.removeStamina
import org.bukkit.Bukkit
import org.bukkit.Input
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.BlockFace
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import java.util.*
import kotlin.math.abs
import kotlin.math.floor

/**
 * [dir] is a unit vector, straight down while the rope hangs still, and [vel] is in blocks per tick.
 * [link] is null while on the piton's own copy of the rope
 */
class Climber(
    val player: UUID,
    piton: ItemDisplay,
    val placed: PlacedPiton,
    val mount: ItemDisplay,
    val riderOffset: Vector,
    private val clearance: Vector,
    link: SectionLink?,
    val minLength: Double,
    val maxLength: Double,
    var length: Double,
) {
    var piton = piton
        private set

    var link: SectionLink? = link
        set(value) {
            field = value
            anchor = hangPoint(value)
        }
    var anchor: Vector = hangPoint(link)
        private set
    val world: World get() = link?.world ?: piton.world

    private fun hangPoint(link: SectionLink?) = placed.ropeAnchor(piton.world, link).toVector().add(clearance)

    var input: Input? = null
    var dir: Vector = straightDown()
    var vel: Vector = Vector()

    /** Blocks per tick, zero once gripped */
    var slideSpeed = 0.0
    private var lastStep = floor(length)
    private var holdDir: Vector? = null

    /** A piton whose chunk reloaded is a new entity with the same UUID, false once there is none */
    fun refreshPiton(): Boolean {
        if (!piton.isValid) (Bukkit.getEntity(piton.uniqueId) as? ItemDisplay)?.let { piton = it }
        return piton.isValid
    }

    val feet: Vector get() = anchor.clone().add(dir.clone().multiply(length))
    val swinging: Boolean get() = dir.y > HANGING_DIR_Y || vel.lengthSquared() > 1e-6
    val swingDir: Vector? get() = dir.takeIf { swinging }

    fun canTopOut(): Boolean {
        if (swinging || placed.face == BlockFace.DOWN) return false
        val block = onCopy(placed.block(piton.world).location).block
        val spot = block.getRelative(placed.face)
        return (1..TOP_OUT_HEADROOM).none { block.getRelative(0, it, 0).hasCollision || spot.getRelative(0, it, 0).hasCollision }
    }

    fun topOutLocation(current: Location) =
        onCopy(Location(piton.world, placed.blockX + 0.5, placed.blockY + 1.0, placed.blockZ + 0.5, current.yaw, current.pitch))

    private fun onCopy(location: Location) = link?.toTarget(location) ?: location

    /** Not checked on grabbing, the end of a rope reaching into the section below only loads once the climber crosses over */
    private fun groundBelow(): Boolean {
        // Nudged down so a rope ending exactly on a block boundary checks the block under it
        val bottom = world.getBlockAt(floor(anchor.x).toInt(), floor(anchor.y - maxLength - 0.01).toInt(), floor(anchor.z).toInt())
        return (0..ropeConfig.groundDismountBuffer).any { bottom.getRelative(0, -it, 0).hasCollision }
    }

    /** A per tick Bukkit teleport costs far more than a plain position update, the passenger follows either way */
    fun moveMount(player: Player, feet: Vector) {
        val body = holdPosition(player, feet)
        mount.toNMS().setPos(body.x - riderOffset.x, body.y - riderOffset.y, body.z - riderOffset.z)
    }

    /** Closer in when the full distance would put the body inside a wall */
    private fun holdPosition(player: Player, feet: Vector): Vector {
        val facing = player.location.direction.setY(0).normalizeOrZero()
        val turned = holdDir?.multiply(1 - HOLD_TURN_RATE)?.add(facing.clone().multiply(HOLD_TURN_RATE))?.normalizeOrZero()?.takeIf { it.lengthSquared() > 0 } ?: facing
        holdDir = turned
        val back = turned.clone().multiply(-ropeConfig.holdDistance)
        return listOf(1.0, 0.5).map { feet.clone().add(back.clone().multiply(it)) }.firstOrNull { !player.collidesAt(it) } ?: feet
    }

    /** Applies the slide or the climb input to the rope length, returns false when the player let go */
    fun updateLength(player: Player): Boolean {
        if (slideSpeed > 0) {
            if (player.uniqueId.canClimb) {
                slideSpeed = (slideSpeed - ropeConfig.slideDeceleration).coerceAtLeast(0.0)
                player.removeStamina((ropeConfig.slideStaminaPerBlock * slideSpeed).toFloat())
            }

            val target = length + slideSpeed
            if (target >= maxLength) {
                val momentum = vel.clone().setY(-slideSpeed)
                RopeClimb.exit(player)
                player.velocity = momentum
                return false
            }
            if (player.collidesAt(anchor.clone().add(dir.clone().multiply(target)))) {
                slideSpeed = 0.0
                return true
            }
            player.fallDistance += slideSpeed.toFloat()
            setLength(player, target)
            return true
        }

        val input = input
        val fast = input?.isSprint == true && player.uniqueId.canClimb
        val speed = ropeConfig.climbSpeed * if (fast) ropeConfig.fastClimbMultiplier else 1.0
        val delta = when {
            input?.isJump == true -> if (input.isSneak) 0.0 else -speed
            input?.isSneak == true -> speed
            else -> 0.0
        }
        if (delta == 0.0) return true

        val target = (length + delta).coerceIn(minLength, maxLength)
        val blocked = target != length && player.collidesAt(anchor.clone().add(dir.clone().multiply(target)))
        // A ledge partway down only stops the descent, one within the last block is the floor the rope ends on
        val atBottom = target >= maxLength || blocked && maxLength - target < 1
        when {
            delta < 0 -> {
                if (blocked) return true
                if (target <= minLength && canTopOut()) {
                    val destination = topOutLocation(player.location)
                    RopeClimb.exit(player)
                    player.teleportAsync(destination)
                    return false
                }
            }
            !swinging && atBottom && groundBelow() -> {
                RopeClimb.exit(player)
                return false
            }
            blocked -> return true
        }

        if (target == length) return true
        if (fast) player.removeStamina((ropeConfig.fastClimbStaminaPerBlock * abs(target - length)).toFloat())
        setLength(player, target)
        return true
    }

    private fun setLength(player: Player, length: Double) {
        this.length = length
        val step = floor(length).takeIf { it != lastStep } ?: return
        lastStep = step
        ropeConfig.sounds.climbStep.play(player.location)
    }

    companion object {
        // Within about 0.8 degrees of straight down the rope counts as hanging still
        private const val HANGING_DIR_Y = -0.9999

        // Share of the way toward where the climber faces that they move around the rope each tick
        private const val HOLD_TURN_RATE = 0.3

        // Room for a standing player on top of the anchor block
        private const val TOP_OUT_HEADROOM = 2

        fun straightDown() = Vector(0, -1, 0)
    }
}
