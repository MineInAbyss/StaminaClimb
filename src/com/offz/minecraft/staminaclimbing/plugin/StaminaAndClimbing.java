package com.offz.minecraft.staminaclimbing.plugin;

import com.offz.minecraft.staminaclimbing.plugin.Climbing.PlayerListener;
import com.offz.minecraft.staminaclimbing.plugin.Stamina.StaminaBar;
import com.offz.minecraft.staminaclimbing.plugin.Stamina.StaminaTask;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public final class StaminaAndClimbing extends JavaPlugin {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(label.equalsIgnoreCase("toggleStamina")) {
            if (sender instanceof Player) {
                Player p = (Player) sender;
                if (StaminaBar.toggled.contains(p.getUniqueId())) {
                    p.sendMessage("Stamina and climbing system: OFF!");
                    StaminaBar.toggled.remove(p.getUniqueId());
                    StaminaBar.registeredBars.get(p.getUniqueId()).removeAll();
                    StaminaBar.registeredBars.remove(p.getUniqueId());
                    return true;
                }
                p.sendMessage("Stamina and climbing system: ON!");
                StaminaBar.toggled.add(p.getUniqueId());
                StaminaBar.registerBar(p);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        getLogger().info("On enable has been called");


        Runnable staminaTask = new StaminaTask();
        getServer().getScheduler().scheduleSyncRepeatingTask(this, staminaTask, 0, 1);

        getServer().getPluginManager().registerEvents(new PlayerListener(), this);
        getServer().getPluginManager().registerEvents(new StaminaBar(), this);

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("onDisable has been invoked!");

//        Map<Player, BossBar> registeredBars = StaminaBar.registeredBars;


//        this.getConfig().set("registeredBars", registeredBars);
//        this.saveConfig();
    }
}
