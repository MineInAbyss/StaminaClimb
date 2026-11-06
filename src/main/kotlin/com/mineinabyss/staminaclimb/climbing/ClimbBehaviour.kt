package com.mineinabyss.staminaclimb.climbing

import com.mineinabyss.geary.papermc.tracking.items.inventory.toGeary
import com.mineinabyss.idofront.nms.aliases.toNMS
import com.mineinabyss.staminaclimb.*
import com.mineinabyss.staminaclimb.component.StaminaModifier
import com.mineinabyss.staminaclimb.events.PlayerStartClimbEvent
import com.mineinabyss.staminaclimb.events.PlayerStopClimbEvent
import com.mineinabyss.staminaclimb.modules.ropeConfig
import com.mineinabyss.staminaclimb.modules.stamina
import com.mineinabyss.staminaclimb.rope.RopeClimb
import com.mineinabyss.staminaclimb.rope.RopeClimb.isRopeClimbing
import com.mineinabyss.staminaclimb.rope.RopeSections
import com.mineinabyss.staminaclimb.stamina.StaminaBar
import com.mineinabyss.staminaclimb.stamina.removeStamina
import io.papermc.paper.entity.TeleportFlag
import io.papermc.paper.event.player.PlayerArmSwingEvent
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.util.Vector
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object ClimbBehaviour : Listener {
    internal var conf = stamina.config
    val canClimb: Object2ObjectOpenHashMap<UUID, Boolean> = Object2ObjectOpenHashMap()
    val isClimbing: MutableMap<UUID, Boolean> = ConcurrentHashMap()
    val cooldown: Object2ObjectOpenHashMap<UUID, Long> = Object2ObjectOpenHashMap()
    private class ClimbStats(var lastY: Double) {
        var up = 0.0
        var down = 0.0

        fun update(y: Double) {
            val delta = y - lastY
            if (delta > 0) up += delta else down -= delta
            lastY = y
        }
    }

    private val climbStats: MutableMap<UUID, ClimbStats> = ConcurrentHashMap()

    /** [speed] is downward in blocks per tick */
    private class GrabSlide(var speed: Double, val fallBefore: Float, var grabY: Double) {
        var lastY = grabY
        var offWallTicks = 0

        fun shift(dy: Double) {
            grabY += dy
            lastY += dy
        }

        fun creditedFall(y: Double, reduction: Double): Float {
            val slid = (grabY - y).coerceAtLeast(0.0)
            return fallBefore + (slid * (1 - reduction)).toFloat()
        }
    }

    private val grabSlides = HashMap<UUID, GrabSlide>()
    fun isGrabSliding(uuid: UUID) = uuid in grabSlides

    fun trackClimbMovement(player: Player) {
        climbStats[player.uniqueId]?.update(player.location.y)
    }

    fun stopClimbing(player: Player) {
        if (player.gameMode == GameMode.SURVIVAL || player.gameMode == GameMode.ADVENTURE) {
            player.allowFlight = false
            player.isFlying = false
        }
        player.flySpeed = 0.1f
        val uuid = player.uniqueId
        grabSlides.remove(uuid)
        val wasClimbing = isClimbing.remove(uuid) != null
        val stats = climbStats.remove(uuid)
        if (wasClimbing) {
            stats?.update(player.location.y)
            val up = stats?.up ?: 0.0
            val down = stats?.down ?: 0.0
            PlayerStopClimbEvent(player, up - down).callEvent()
        }
    }

//    private val climbableMap = mutableMapOf<UUID, Float>()
//    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
//    fun PlayerMoveEvent.onScaleDownClimbable() {
//        if (!hasExplicitlyChangedBlock() || !player.isInClimbableBlock) return
//
//        val block = player.location.block
//        val increase = if (block.type == Material.SCAFFOLDING || !player.climbEnabled || !player.uniqueId.canClimb) 1 else 0
//        climbableMap.compute(player.uniqueId) { _, v -> (v ?: 0).toFloat() + increase }
//        if (block.getRelative(BlockFace.DOWN).type in Tag.CLIMBABLE.values) return
//        player.hurtBones(climbableMap[player.uniqueId] ?: 0f)
//        climbableMap.remove(player.uniqueId)
//    }

    @EventHandler
    fun BlockPlaceEvent.onBlockPlace() {
        val uuid = player.uniqueId
        if ((!player.isSneaking || player.isInClimbableBlock) && uuid.isClimbing) isCancelled = true
        if (uuid in cooldown) uuid.climbCooldown = conf.walljumpCooldown
    }

    @EventHandler
    fun BlockBreakEvent.onBlockBreak() {
        val uuid = player.uniqueId
        if (uuid in cooldown) uuid.climbCooldown = conf.walljumpCooldown
    }

    @EventHandler
    fun PlayerInteractEvent.onRightClick() {
        val uuid = player.uniqueId

        //if sneaking, don't climb, but do climb if player is also falling
        // A false entry is the air time left after a dash or leaving a wall, which a fresh grab replaces
        if (allowClimb(player) && rightClicked() && !uuid.isClimbing && uuid !in grabSlides) {
            val stamina = StaminaBar.players[uuid] ?: return
            if (stamina.progress > 0) {
                if (player.wallDifficulty >= 0 || pullToWall()) grab(player)
                else if (uuid !in isClimbing) uuid.restartCooldown()
            }
        }

        // Only clicks on blocks are held back mid slide, so buckets, pearls and food still work while slipping
        val sliding = uuid in grabSlides && action == Action.RIGHT_CLICK_BLOCK
        if ((!player.isSneaking || player.isInClimbableBlock) && (uuid.isClimbing || sliding)) isCancelled = true
    }

    private fun grab(player: Player) {
        val fallSpeed = player.fallSpeed
        // A leftover air time entry would have the climb loop stick the player to the wall mid slide
        if (player.uniqueId in isClimbing) stopClimbing(player)
        if (fallSpeed > 0) grabSlides[player.uniqueId] = GrabSlide(fallSpeed, player.fallDistance, player.location.y)
        else startClimbing(player)
    }

    /** The wall check only reaches a hair past the player, so a wall clicked from a little further off pulls them flush against it */
    private fun PlayerInteractEvent.pullToWall(): Boolean {
        val wall = wallBlock ?: return false
        val face = blockFace.takeIf { it.modY == 0 } ?: return false
        val from = player.location
        val gap = player.standingHalfWidth
        val at = from.clone().apply {
            if (face.modX != 0) x = wall.x + 0.5 + face.modX * (0.5 + gap)
            if (face.modZ != 0) z = wall.z + 0.5 + face.modZ * (0.5 + gap)
        }
        val pull = at.toVector().subtract(from.toVector())
        // Only ever toward the wall, a player already past the face would be pushed out of it
        if (pull.dot(face.direction) > 0 || pull.lengthSquared() > conf.grabReach * conf.grabReach) return false
        if (player.collidesAt(at.toVector()) || player.wallDifficultyAt(at) < 0) return false
        return player.teleport(at, TeleportFlag.Relative.VELOCITY_X, TeleportFlag.Relative.VELOCITY_Y, TeleportFlag.Relative.VELOCITY_Z)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun PlayerTeleportEvent.onTeleportMidSlide() {
        val slide = grabSlides[player.uniqueId] ?: return
        // A section transition carries the slide on in the next section, its fall credit measured from the moved grab point
        if (to.world == from.world && cause == PlayerTeleportEvent.TeleportCause.PLUGIN && RopeSections.inTransition(from)) slide.shift(to.y - from.y)
        else grabSlides.remove(player.uniqueId)
    }

    @EventHandler
    fun PlayerDeathEvent.onDeathMidSlide() {
        grabSlides.remove(entity.uniqueId)
    }

    /** Landing is damaged by the server before this runs, so the fall distance is kept at the landing credit every tick and only raised to the lost grip credit when they let go */
    fun tickGrabSlides() = grabSlides.entries.removeIf { (uuid, slide) ->
        val player = Bukkit.getPlayer(uuid) ?: return@removeIf true
        if (player.toNMS().onGround()) return@removeIf true

        val y = player.location.y
        player.removeStamina((ropeConfig.slideStaminaPerBlock * (slide.lastY - y).coerceAtLeast(0.0)).toFloat())
        slide.lastY = y
        slide.offWallTicks = if (player.wallDifficulty < 0) slide.offWallTicks + 1 else 0
        if (!uuid.canClimb || slide.offWallTicks > conf.grabSlideGripGraceTicks) {
            player.fallDistance = slide.creditedFall(y, conf.grabSlideLostGripFallReduction)
            return@removeIf true
        }

        // A slide that stops away from the wall hangs there until it finds the wall again or the grace runs out
        slide.speed = (slide.speed - ropeConfig.slideDeceleration).coerceAtLeast(0.0)
        if (slide.speed == 0.0 && slide.offWallTicks == 0) {
            player.fallDistance = 0f
            startClimbing(player)
            return@removeIf true
        }
        player.fallDistance = slide.creditedFall(y, conf.grabSlideLandedFallReduction)
        player.velocity = Vector(0.0, -slide.speed, 0.0)
        false
    }

    @EventHandler
    fun PlayerArmSwingEvent.onLeftClick() {
        if (hand != EquipmentSlot.HAND) return
        if (player.isRopeClimbing || RopeClimb.swingConsumed(player)) return
        val uuid = player.uniqueId
        //when isClimbing is false, it means player can still do the jump, once it's actually removed from the hashmap, that's when we can't climb
        //don't even ask ok
        if (allowClimb(player) && uuid in isClimbing) {
            //set a cooldown for player not to be able to wall jump right away
            uuid.climbCooldown = conf.walljumpCooldown
            if (uuid !in StaminaBar.players) return

            //find left clicked block (in adventure mode)
            val blocks = player.getLastTwoTargetBlocks(null, 4) //Get two connected blocks player is looking at
            if (blocks.size < 2 || blocks[0].isLiquid || blocks[0].location.distanceSquared(player.location) < 4) return

            if (isClimbable(blocks[1].type)) dash(player)
        }
    }

    fun startClimbing(player: Player) {
        val uuid = player.uniqueId
        //jump a bit if player is standing on ground and starts climbing
        if (player.toNMS().onGround())
            player.velocity = player.velocity.add(Vector(0.0, 0.25, 0.0))

        uuid.isClimbing = true
        climbStats[uuid] = ClimbStats(player.location.y)
        player.allowFlight = true
        player.isFlying = true
        player.flySpeed = player.climbFlySpeed
        PlayerStartClimbEvent(player).callEvent()
    }

    fun dash(player: Player) {
        val uuid = player.uniqueId
        if (uuid !in StaminaBar.players) return
        val direction = player.location.direction
        val x = direction.x
        val y = direction.y
        val z = direction.z

        if (player.wallDifficulty < 0) { //if not at a wall (i.e. double jump)
            player.removeStamina(0.25f) //take away more stamina when in the air
            player.velocity = Vector(x / 1.8, y / 2 + 0.3, z / 1.8)
            uuid.climbCooldown = -conf.airTime
        } else {
            player.removeStamina(0.2f)
            player.velocity = Vector(x / 1.8, y / 1, z / 1.8)
        }
    }

    private val Player.climbFlySpeed: Float
        get() = inventory.toGeary()?.equipmentModifiers(0.03f, StaminaModifier.ModifierType.CLIMB_SPEED) ?: 0.03f

    fun mayClimbWall(player: Player): Boolean {
        val uuid = player.uniqueId
        return player.climbEnabled && uuid.canClimb && uuid.climbCooldownDone
                && (player.gameMode == GameMode.SURVIVAL || player.gameMode == GameMode.ADVENTURE)
    }

    // Sneaking stops a climb from starting unless the player is already falling
    private fun allowClimb(player: Player) = mayClimbWall(player) && !player.isRopeClimbing && (!player.isSneaking || player.fallSpeed > 0)

    /** A torch, vine or other thin block on the wall catches the click meant for the wall behind it */
    private val PlayerInteractEvent.wallBlock: Block?
        get() = clickedBlock?.let { if (it.type.isSolid || !isClimbable(it.type)) it else it.getRelative(blockFace.oppositeFace) }

    private fun PlayerInteractEvent.rightClicked(): Boolean { //did player do a valid right click
        if (action != Action.RIGHT_CLICK_BLOCK || hand != EquipmentSlot.HAND || player.heldPiton != null) return false
        val block = wallBlock?.type ?: return false
        if (player.holdsBlock || !isClimbable(block)) {
            player.uniqueId.climbCooldown = conf.jumpCooldown
            return false
        }
        return block.isSolid
    }

    /** Blocks players interact with, such as containers and doors, are used rather than climbed */
    fun isClimbable(block: Material) = block !in conf.climbBlacklist && conf.climbBlacklistGeneral.none { it in block.name }

    private fun cooldownComplete(uuid: UUID): Boolean { //is the click cooldown complete
        val playerCooldown = cooldown[uuid] ?: return true
        //If time indicated in cooldown reached, cooldown is complete
        return playerCooldown <= System.currentTimeMillis()
    }
}
