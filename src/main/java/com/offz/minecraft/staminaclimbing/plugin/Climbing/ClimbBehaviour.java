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
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ClimbBehaviour implements Listener {

    public static Map<UUID, Integer> jumpCount = new HashMap<>();

    @EventHandler()
    public static void onRightClick(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        if (!StaminaBar.toggled.contains(p.getUniqueId()) || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR)
            return; //Make sure player is in survival and has climb system enabled, toggle is temporary

        BossBar b = StaminaBar.registeredBars.get(p.getUniqueId());
        double progress = b.getProgress();
        int playerJumpCount = jumpCount.get(p.getUniqueId());

        if (e.getAction().equals(Action.RIGHT_CLICK_BLOCK) && e.getClickedBlock().getType().isSolid() && progress > 0.1 && p.getInventory().getItemInMainHand().getType().equals(Material.AIR)) {

            Location L1 = p.getLocation();
            Location L2 = e.getClickedBlock().getLocation();
            L2.setX(L2.getX() + 0.5);L2.setY(L2.getY() - 0.5);L2.setZ(L2.getZ() + 0.5);//Get location at center of block

            double distance = L1.distance(L2);
            float fallDistance = p.getFallDistance();

            Vector velocity = p.getVelocity();

            if (p.isSneaking() && distance < 3) { //Wall slide
                p.setVelocity(velocity.setY(Math.signum(velocity.getY()) * ((Math.abs(velocity.getY()) - 1) + Math.pow(Math.abs(20 * velocity.getY()) + 1, -0.03))));//Slow down fall
                p.setVelocity((new Vector((velocity.getX() + p.getLocation().getDirection().getX() / 30) * 0.8, p.getVelocity().getY(), (velocity.getZ() + p.getLocation().getDirection().getZ() / 30) * 0.8)));//Bring player towards the direction they are facing (pull towards wall)
                p.setFallDistance((float) Math.abs((fallDistance - 1) + Math.pow(0.175 * fallDistance + 1, -5)));//Reduce fall damage
                if (progress - 0.013 >= 0) {
                    b.setProgress(progress - 0.013);
                }
                return;
            }


            if (playerJumpCount <= 2) { //Right clicking once triggers onRightClick twice, thus <= 2 means only 2 air jumps, if this were <= 4, this would mean 3 air jumps
                if (e.getBlockFace() == BlockFace.UP && distance < 2.4 && !p.isOnGround()) {
                    p.setVelocity(p.getVelocity().setY(0.35));
                } else if (e.getBlockFace() != BlockFace.UP && fallDistance < 4) { //Stop players from jumping while falling
                    p.setVelocity(p.getVelocity().setY(0.35));
                } else {
                    return;
                }
            }
            p.setFallDistance(p.getFallDistance() / 1.2f);
            jumpCount.put(p.getUniqueId(), playerJumpCount + 1); //This is triggered twice because of spigot
            //TODO Possibly implement a wait time so 1 right click does not count as 2
            if (progress - 0.01 >= 0) {
                b.setProgress(progress - 0.01);
            }
            /*switch (e.getBlockFace()) { //Code for player jumping off wall while shifting, most likely will be replaced by wall slide
                case UP:
                    if (distance < 2.4 && !p.isOnGround()) {
                        p.setVelocity(velocity.setY(0.4 * multiplier));
                        break;
                    }
                    return;
                case EAST:
                    if (distance < 2.4 && p.isSneaking()) {
                        p.setVelocity(new Vector(0.4, 0.5 * multiplier, velocity.getZ()));
                    } else {
                        p.setVelocity(new Vector(velocity.getX(), 0.3 * multiplier, velocity.getZ()));
                    }
                    break;
                case WEST:
                    if (distance < 2.4 && p.isSneaking()) {
                        p.setVelocity(new Vector(-0.4, 0.5 * multiplier, velocity.getZ()));
                    } else {
                        p.setVelocity(new Vector(velocity.getX(), 0.3 * multiplier, velocity.getZ()));
                    }
                    break;
                case NORTH:
                    if (distance < 2.4 && p.isSneaking()) {
                        p.setVelocity(new Vector(velocity.getX(), 0.5 * multiplier, -0.4));
                    } else {
                        p.setVelocity(new Vector(velocity.getX(), 0.3 * multiplier, velocity.getZ()));
                    }
                    break;
                case SOUTH:
                    if (distance < 2.4 && p.isSneaking()) {
                        p.setVelocity(new Vector(velocity.getX(), 0.5 * multiplier, 0.4));
                    } else {
                        p.setVelocity(new Vector(velocity.getX(), 0.3 * multiplier, velocity.getZ()));
                    }
                    break;
            }*/
        }
    }

    @EventHandler()
    public void onLeftClick(PlayerAnimationEvent e) {
        Player p = e.getPlayer();

        if (!StaminaBar.toggled.contains(p.getUniqueId()) || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR)//Toggle may be temporary
            return;

        BossBar b = StaminaBar.registeredBars.get(p.getUniqueId());
        double progress = b.getProgress();
        int playerJumpCount = jumpCount.get(p.getUniqueId());
        Vector velocity = p.getVelocity();

        List<Block> blocks = p.getLastTwoTargetBlocks(null, 4); //Get two connected blocks player is looking at
        BlockFace blockFace = blocks.get(1).getFace(blocks.get(0)); //Find the face between both of these blocks

        if (blocks.get(1).getType() != Material.AIR && playerJumpCount <= 4 && playerJumpCount > 0) { //Make sure target block isn't air

            Vector direction = p.getLocation().getDirection();

            switch (blockFace) {//Leap forward on left click, after already right-clicked
                case UP:
                    if (!p.isOnGround() && playerJumpCount <= 2) {
                        p.setVelocity(velocity.setY(0.35));
                        jumpCount.put(p.getUniqueId(), playerJumpCount - 2);
                        break;
                    }
                    return;
                case EAST:
                case WEST:
                    p.setVelocity(new Vector(velocity.getX(), 0.3, Math.signum(direction.getZ()) * 0.5));
                    break;
                case NORTH:
                case SOUTH:
                    p.setVelocity(new Vector(Math.signum(direction.getX()) * 0.5, 0.3, velocity.getZ()));
                    break;
            }
            p.setFallDistance(p.getFallDistance() / 1.2f);
            jumpCount.put(p.getUniqueId(), playerJumpCount + 4);
            if (progress - 0.02 >= 0) {
                b.setProgress(progress - 0.02);
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!StaminaBar.toggled.contains(e.getPlayer().getUniqueId())) return;//Toggle
        if ((e.getPlayer().getGameMode() == GameMode.ADVENTURE || e.getPlayer().getGameMode() == GameMode.SURVIVAL) && e.getPlayer().isOnGround())//Reset jump count when player hits ground
            jumpCount.put(e.getPlayer().getUniqueId(), 0);
    }
}