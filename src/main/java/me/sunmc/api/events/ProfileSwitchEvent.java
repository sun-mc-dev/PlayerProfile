package me.sunmc.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired when a player switches profiles.
 * The pre-switch event (before switching) is cancellable.
 * The post-switch event (after switching) is not cancellable.
 *
 * @author SunMC Development Team
 * @version 1.0.0
 */
public class ProfileSwitchEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String fromProfile;
    private final String toProfile;
    private final boolean post;
    private boolean cancelled;

    /**
     * Creates a new ProfileSwitchEvent.
     *
     * @param player      the player switching profiles
     * @param fromProfile the current profile name
     * @param toProfile   the target profile name
     * @param post        true if this is fired after the switch, false if before
     */
    public ProfileSwitchEvent(Player player, String fromProfile, String toProfile, boolean post) {
        this.player = player;
        this.fromProfile = fromProfile;
        this.toProfile = toProfile;
        this.post = post;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * Gets the player switching profiles.
     *
     * @return the player
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Gets the profile being switched from.
     *
     * @return the source profile name
     */
    public String getFromProfile() {
        return fromProfile;
    }

    /**
     * Gets the profile being switched to.
     *
     * @return the target profile name
     */
    public String getToProfile() {
        return toProfile;
    }

    /**
     * Checks if this is a post-switch event.
     *
     * @return true if fired after the switch
     */
    public boolean isPost() {
        return post;
    }

    /**
     * Checks if this is a pre-switch event.
     *
     * @return true if fired before the switch
     */
    public boolean isPre() {
        return !post;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        if (post) {
            throw new IllegalStateException("Cannot cancel a post-switch event");
        }
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
}