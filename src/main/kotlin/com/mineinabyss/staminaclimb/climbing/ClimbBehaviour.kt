package com.mineinabyss.staminaclimb.climbing

import com.mineinabyss.staminaclimb.*
import com.mineinabyss.staminaclimb.config.config
import com.mineinabyss.staminaclimb.stamina.StaminaBar
import com.mineinabyss.staminaclimb.stamina.addProgress
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerAnimationEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.util.Vector
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object ClimbBehaviour : Listener {
    val canClimb: MutableMap<UUID, Boolean> = mutableMapOf()
    val isClimbing: MutableMap<UUID, Boolean> = ConcurrentHashMap()
    val cooldown: MutableMap<UUID, Long> = HashMap()

    fun stopClimbing(player: Player) {
        if (player.gameMode == GameMode.SURVIVAL || player.gameMode == GameMode.ADVENTURE) {
            player.allowFlight = false
            player.isFlying = false
        }
        player.flySpeed = 0.1f
        val uuid = player.uniqueId
        isClimbing.remove(uuid)
    }

    @EventHandler
    fun BlockPlaceEvent.onBlockPlace() {
        val uuid = player.uniqueId
        if (!player.isSneaking && uuid.isClimbing) isCancelled = true
        if (cooldown.containsKey(uuid)) uuid.climbCooldown = config.walljumpCooldown
    }

    @EventHandler
    fun BlockBreakEvent.onBlockBreak() {
        val uuid = player.uniqueId
        if (cooldown.containsKey(uuid)) uuid.climbCooldown = config.walljumpCooldown
    }

    @EventHandler
    fun PlayerInteractEvent.onRightClick() {
        val uuid = player.uniqueId
        val velocity = player.velocity

        //if sneaking, don't climb, but do climb if player is also falling
        if (allowClimb(player) && rightClicked() && !isClimbing.containsKey(uuid)) {
            val bossBar = StaminaBar.registeredBars[uuid] ?: return
            //remove stamina progress based on how long the player's already fallen
            bossBar.addProgress(-player.fallDistance / 15f)
            //reduce fall damage by half heart per feather fall level
            val damageAmount = (player.fallDistance - 3) / 1.9
            if (damageAmount >= 1) //prevent player taking damage they can't see, which just makes a sound
                player.damage(damageAmount)

            if (bossBar.progress() > 0)
                if (player.wallDifficulty >= 0) {
                    //jump a bit if player is standing on ground and starts climbing
                    if (velocity.y in -0.08..-0.07)
                        player.velocity = player.velocity.add(Vector(0.0, 0.25, 0.0))

                    uuid.isClimbing = true
                    player.allowFlight = true
                    player.isFlying = true
                } else {
                    isClimbing.remove(uuid)
                    uuid.restartCooldown()
                }
            player.flySpeed = 0.03f
        }
    }

    @EventHandler
    fun PlayerAnimationEvent.onLeftClick() {
        val uuid = player.uniqueId
        //when isClimbing is false, it means player can still do the jump, once it's actually removed from the hashmap, that's when we can't climb
        //don't even ask ok
        if (allowClimb(player) && isClimbing.containsKey(uuid)) {
            //set a cooldown for player not to be able to wall jump right away
            uuid.climbCooldown = config.walljumpCooldown
            val bossBar = StaminaBar.registeredBars[uuid] ?: return

            //find left clicked block (in adventure mode)
            val blocks = player.getLastTwoTargetBlocks(null, 4) //Get two connected blocks player is looking at
            if (blocks.size < 2 || blocks[0].isLiquid || blocks[0].location.distanceSquared(player.location) < 4) return

            //leap
            val blockType = blocks[1].type
            if (leftClicked(blockType)) { //Make sure target block isn't in blacklist
                val direction = player.location.direction
                val x = direction.x
                val y = direction.y
                val z = direction.z

                if (player.wallDifficulty < 0) { //if not at a wall (i.e. double jump)
                    bossBar.addProgress(-0.25f) //take away more stamina when in the air
                    player.velocity = player.velocity.apply {
                        this.x = x / 1.8
                        this.y = y / 2 + 0.3
                        this.z = z / 1.8
                    }
                    uuid.climbCooldown = -config.airTime
                } else {
                    bossBar.addProgress(-0.2f)
                    player.velocity = player.velocity.apply {
                        this.x = x / 1.8
                        this.y = y / 1
                        this.z = z / 1.8
                    }
                }
            }
        }
    }

    private fun allowClimb(player: Player): Boolean { //does player meet all requirements to be able to climb
        val uuid = player.uniqueId
        //Check if player in survival or adventure mode
        return (player.climbEnabled && uuid.canClimb
                && (!player.isSneaking || player.velocity.y < -0.5) //prevent climb when sneaking, unless already falling
                && uuid.climbCooldownDone
                && (player.gameMode == GameMode.SURVIVAL || player.gameMode == GameMode.ADVENTURE))
        //              && p.getInventory().getItemInMainHand().getType().equals(Material.AIR)) //Make sure player is holding nothing in hand
    }

    private fun PlayerInteractEvent.rightClicked(): Boolean { //did player do a valid right click
        if (clickedBlock == null) return false
        val block = clickedBlock?.type ?: return false
        val heldItem = player.inventory.itemInMainHand.type
        if (heldItem.isBlock && heldItem != Material.AIR) {
            player.uniqueId.climbCooldown = config.jumpCooldown
            return false
        }
        if (config.climbBlacklist.contains(block)) {
            player.uniqueId.climbCooldown = config.jumpCooldown
            return false
        } else {
            for (interactable in config.climbBlacklistGeneral) {
                if (block.toString().contains(interactable)) {
                    player.uniqueId.climbCooldown = config.jumpCooldown
                    return false
                }
            }
        }
        return action == Action.RIGHT_CLICK_BLOCK && hand == EquipmentSlot.HAND && block.isSolid
    }

    //TODO dont make checks like this separate for left/right click if they do basically the same thing
    private fun leftClicked(block: Material): Boolean { //did player do a valid left click
        //If clicked block is in blacklist, return false
        return !config.climbBlacklist.contains(block)
    }

    private fun cooldownComplete(uuid: UUID): Boolean { //is the click cooldown complete
        val playerCooldown = cooldown[uuid] ?: return true
        //If time indicated in cooldown reached, cooldown is complete
        return playerCooldown <= System.currentTimeMillis()
    }
}
