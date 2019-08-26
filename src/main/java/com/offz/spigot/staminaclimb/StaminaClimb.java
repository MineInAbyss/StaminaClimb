package com.offz.spigot.staminaclimb;

import com.offz.spigot.staminaclimb.Climbing.ClimbBehaviour;
import com.offz.spigot.staminaclimb.Stamina.StaminaBar;
import com.offz.spigot.staminaclimb.Stamina.StaminaTask;
import org.bukkit.Bukkit;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class StaminaClimb extends JavaPlugin {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("toggleStamina") || label.equalsIgnoreCase("climb")) { //Stamina toggle
            if (sender.hasPermission("staminaclimb.toggle")) {
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    UUID uuid = p.getUniqueId();
                    if (StaminaBar.toggled.contains(uuid)) {
                        p.sendMessage("Stamina and climbing system: ON!");
                        StaminaBar.registerBar(p);
                        return true;
                    }
                    p.sendMessage("Stamina and climbing system: OFF!");
                    StaminaBar.toggled.add(uuid);
                    StaminaBar.unregisterBar(uuid);
                    ClimbBehaviour.stopClimbing(p);
                    return true;
                }
            } else
                sender.sendMessage("You do not have the permission to use this command");
        }
        return false;
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        for (Player p : Bukkit.getOnlinePlayers()) { //toggle system on for all online players (for plugin reload)
            StaminaBar.registerBar(p);
        }

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

        //prevent players from getting access to flight after server restart/plugin reload
        for (UUID uuid : ClimbBehaviour.isClimbing.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null)
                ClimbBehaviour.stopClimbing(p);
        }

        //stop stamina bars from duplicating on plugin reload
        for (BossBar b : StaminaBar.registeredBars.values()) {
            b.removeAll();
        }
    }
}