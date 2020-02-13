package com.offz.spigot.staminaclimb.ClimbingAxe;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class ClimbingAxeProperties implements Listener {
    @EventHandler
    public void onBlockBreakWpick(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block block = e.getBlock();

        if (p.getInventory().getItemInMainHand().getType() == Material.IRON_PICKAXE && p.getInventory().getItemInMainHand().getItemMeta().getDisplayName().equals(ChatColor.LIGHT_PURPLE + "Climbing Axe")) {
            if (block.getType() != (Material.STONE)) {
                e.setCancelled(true);
                return;
            }
        }
    }
}