package com.offz.spigot.staminaclimb.ClimbingAxe;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ClimbingAxeProperties implements Listener {



//todo add damage to item when used?
    @EventHandler
    public void onBlockBreakWpick(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block block = e.getBlock();
        ItemStack itemStack = new ItemStack(Material.IRON_PICKAXE);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Climbing Axe");
        itemStack.setItemMeta(itemMeta);

        if (p.getInventory().getItemInMainHand().equals(itemStack) && p.getInventory().getItemInMainHand().getItemMeta().equals(itemMeta)) {
            if (block.getType() != (Material.AIR)) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
