package com.offz.minecraft.staminaclimbing.plugin.Climbing;

import com.offz.minecraft.staminaclimbing.plugin.Stamina.StaminaBar;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class PlayerListener implements Listener {
    @EventHandler()
    public void onClick(PlayerInteractEvent e) {
        //PlayerInteractBehaviour.onClick(e); //I want to make this work like the RelicUseListener but I'd need to have behaviour classes in a static context, as we don't have something like Relic Behaviour to attach it to.
        if (e.getPlayer().getGameMode() == GameMode.ADVENTURE || e.getPlayer().getGameMode() == GameMode.SURVIVAL)
            ClimbBehaviour.onClick(e);
    }
    @EventHandler()
    public void onLeftClick(PlayerAnimationEvent e) {
        //PlayerInteractBehaviour.onClick(e); //I want to make this work like the RelicUseListener but I'd need to have behaviour classes in a static context, as we don't have something like Relic Behaviour to attach it to.
        if (e.getPlayer().getGameMode() == GameMode.ADVENTURE || e.getPlayer().getGameMode() == GameMode.SURVIVAL)
            ClimbBehaviour.onLeftClick(e);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getPlayer().getGameMode() == GameMode.ADVENTURE || e.getPlayer().getGameMode() == GameMode.SURVIVAL)
            ClimbBehaviour.onFall(e);
    }
}
