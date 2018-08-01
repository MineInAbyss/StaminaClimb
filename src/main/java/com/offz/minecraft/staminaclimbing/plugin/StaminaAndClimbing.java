package com.offz.minecraft.staminaclimbing.plugin;

import com.offz.minecraft.staminaclimbing.plugin.Climbing.ClimbBehaviour;
import com.offz.minecraft.staminaclimbing.plugin.Stamina.StaminaBar;
import com.offz.minecraft.staminaclimbing.plugin.Stamina.StaminaTask;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;


public final class StaminaAndClimbing extends JavaPlugin {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        //Stamina toggle
        if (label.equalsIgnoreCase("toggleStamina")) {
            if (sender instanceof Player) {
                Player p = (Player) sender;
                UUID uuid = p.getUniqueId();
                if (StaminaBar.toggled.contains(uuid)) {
                    p.sendMessage("Stamina and climbing system: OFF!");
                    StaminaBar.unregisterBar(uuid);
                    StaminaBar.toggled.remove(uuid);
                    ClimbBehaviour.cooldown.remove(uuid);
                    return true;
                }
                p.sendMessage("Stamina and climbing system: ON!");
                StaminaBar.toggled.add(uuid);
                ClimbBehaviour.cooldown.put(uuid, System.currentTimeMillis());
                StaminaBar.registerBar(p);
                return true;
            }
        } return false;
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        getLogger().info("On enable has been called");

        Runnable staminaTask = new StaminaTask();
        getServer().getScheduler().scheduleSyncRepeatingTask(this, staminaTask, 0, 1);

        getServer().getPluginManager().registerEvents(new ClimbBehaviour(), this);
        getServer().getPluginManager().registerEvents(new StaminaBar(), this);

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("onDisable has been invoked!");
    }
}