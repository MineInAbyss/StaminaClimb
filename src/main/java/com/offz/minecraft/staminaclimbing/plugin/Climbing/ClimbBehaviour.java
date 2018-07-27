package com.offz.minecraft.staminaclimbing.plugin.Climbing;

import com.offz.minecraft.staminaclimbing.plugin.Stamina.StaminaBar;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ClimbBehaviour implements Listener {

    public static Map<UUID, Integer> jumpCount = new HashMap<>();
    public static Map<UUID, Boolean> canClimb = new HashMap<>();
    public static Map<UUID, Integer> cooldown = new HashMap<>();

    @EventHandler()
    public static void onRightClick(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        if (!StaminaBar.toggled.contains(uuid) || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) //Make sure player is in survival and has climb system enabled
            return;

        BossBar b = StaminaBar.registeredBars.get(uuid);
        boolean playerCanClimb = canClimb.get(uuid);

        int playerJumpCount = 0;
        if (jumpCount.containsKey(uuid)) //Remove any chance of null pointer
            playerJumpCount = jumpCount.get(uuid);

        //Cooldown so player does not trigger twice accidentally
        int playerCooldown = 0;
        if (cooldown.containsKey(uuid)) {
            playerCooldown = cooldown.get(uuid);
            if (playerCooldown == 0)
                cooldown.remove(uuid);
        }

        if (e.getAction().equals(Action.RIGHT_CLICK_BLOCK) && e.getHand().equals(EquipmentSlot.HAND) //Check right click, e.getHand makes event only trigger once, otherwise spigot calls it twice
                && playerCanClimb
                && e.getClickedBlock().getType().isSolid()
                && p.getInventory().getItemInMainHand().getType().equals(Material.AIR)) { //Make sure player is holding nothing in hand
            Location L1 = p.getLocation();
            Location L2 = e.getClickedBlock().getLocation();
            L2.add(0.5, -0.5, 0.5);//Get location at center of block
            double distance = L1.distance(L2);

            if (p.isSneaking()) { //Wall slide
                if (p.isOnGround() || distance > 2) //If player is sneaking on ground, do not perform slide, nor jump
                    return;
                //Calculate slowdown based on current velocity
                Vector velocity = p.getVelocity();
                double y = p.getVelocity().getY();
                double slowdown;
                if (y > -0.85)
                    slowdown = 1.35;
                else if (y > -1.25)
                    slowdown = 1.12;
                else if (y > -2)
                    slowdown = 1.06;
                else
                    slowdown = 1.02;
                p.setVelocity(velocity.setY(y / slowdown));

                Vector direction = p.getLocation().getDirection();
                y = p.getVelocity().getY(); //Update to account for slowdown

                //Bring player towards the direction they are facing (pull towards wall)
                p.setVelocity((new Vector((velocity.getX() + direction.getX() / 20) * 0.95, y, (velocity.getZ() + direction.getZ() / 20) * 0.95)));

                //Remove stamina based on distance fallen
                double removeStamina = Math.abs(0.01 - y / 20);
                StaminaBar.removeProgress(removeStamina, b);
            } else if (playerJumpCount <= 1 && playerCooldown == 0) { //Climb jump (right click)
                jumpY(p, e.getBlockFace(), distance, b);
                cooldown.put(uuid, 2); //Set a cooldown of 2 ticks
            }
        }
    }

    @EventHandler()
    public void onLeftClick(PlayerAnimationEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        if (!StaminaBar.toggled.contains(uuid) || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR)
            return;

        BossBar b = StaminaBar.registeredBars.get(uuid);
        int playerJumpCount = jumpCount.get(uuid);

        //Find left clicked block (in adventure mode)
        List<Block> blocks = p.getLastTwoTargetBlocks(null, 4); //Get two connected blocks player is looking at
        BlockFace blockFace = blocks.get(1).getFace(blocks.get(0)); //Find the face between both of these blocks

        //Horizontal leap
        if (blocks.get(1).getType() != Material.AIR && playerJumpCount <= 2 && playerJumpCount >= 1) { //Make sure target block isn't air
            Vector direction = p.getLocation().getDirection();
            switch (blockFace) {
                case UP:
                    if (!p.isOnGround() && playerJumpCount <= 1) {
                        jumpY(p, blockFace, 4, b);
                        return; //This action is the same as jumping (right clicking) on top of block, so it does not proceed further
                    }
                    return;
                case EAST:
                case WEST:
                    jumpY(p, blockFace, 4, b);
                    p.setVelocity(p.getVelocity().setZ(Math.signum(direction.getZ()) * 0.5));
                    break;
                case NORTH:
                case SOUTH:
                    jumpY(p, blockFace, 4, b);
                    p.setVelocity(p.getVelocity().setX(Math.signum(direction.getX()) * 0.5));
                    break;
            }
            p.setFallDistance(p.getFallDistance() * 0.8f);//Reduce less fall damage than regular jump
            jumpCount.put(uuid, playerJumpCount + 1);//Add extra to jumpCount so player cannot jump after left clicking
            StaminaBar.removeProgress(0.05, b);//Use slightly more stamina than regular jump
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        if (!StaminaBar.toggled.contains(uuid)) return;//Toggle
        if ((p.getGameMode() == GameMode.ADVENTURE || p.getGameMode() == GameMode.SURVIVAL) && p.isOnGround())//Reset jump count when player hits ground
            jumpCount.put(uuid, 0);
    }

    private static void jumpY(Player p, BlockFace blockFace, double distance, BossBar b) {
        Vector velocity = p.getVelocity();
        UUID uuid = p.getUniqueId();
        double y = velocity.getY();
        //While falling, perform slowdown instead of jump
        if (y < -0.9) {
            //When velocity at -0.9, velocity becomes -0.25 (same as regular jump), with higher speed, stays closer to its original value
            p.setVelocity(velocity.setY(y * ((1 / (1.2 * y + 0.28)) + 1)));
        } else if (y < -0.3) {
            p.setVelocity(velocity.setY(0.225));
        } else if (blockFace == BlockFace.UP && distance < 2.4 && !p.isOnGround()) {
            p.setVelocity(velocity.setY(0.37));
        } else if (blockFace != BlockFace.UP) {
            p.setVelocity(velocity.setY(0.37));
        }

        p.setFallDistance(p.getFallDistance() / 1.5f);
        StaminaBar.removeProgress(0.05, b);
        jumpCount.put(uuid, jumpCount.get(uuid) + 1);
    }
}