package org.bukkit;

import org.bukkit.permissions.ServerOperator;

public interface OfflinePlayer extends ServerOperator {
    /**
     * Checks if this player is currently online
     *
     * @return true if they are online
     */
    public boolean isOnline();

    /**
     * Returns the name of this player
     *
     * @return Player name
     */
    public String getName();

    /**
     * Checks if this player is banned or not
     *
     * @return true if banned, otherwise false
     */
    public boolean isBanned();

    /**
     * Bans or unbans this player
     *
     * @param banned true if banned
     */
    public void setBanned(boolean banned);
    
    /**
     * Bans this player with a reason
     *
     * @param banned true if banned
     * @param reason the reason for the ban
     */
    public void setBanned(boolean banned, String reason);
    
    /**
     * Gets the ban reason for this player
     *
     * @return the ban reason, or null if not banned or no reason set
     */
    public String getBanReason();

    /**
     * Checks if this player is whitelisted or not
     *
     * @return true if whitelisted
     */
    public boolean isWhitelisted();

    /**
     * Sets if this player is whitelisted or not
     *
     * @param value true if whitelisted
     */
    public void setWhitelisted(boolean value);
}
