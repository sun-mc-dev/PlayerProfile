package me.sunmc.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Event fired when a player's profiles are loaded.
 *
 * @author SunMC Development Team
 * @version 1.0.0
 */
public class ProfileLoadEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final List<String> profileNames;

    public ProfileLoadEvent(Player player, List<String> profileNames) {
        this.player = player;
        this.profileNames = profileNames;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public Player getPlayer() {
        return player;
    }

    public List<String> getProfileNames() {
        return profileNames;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
}