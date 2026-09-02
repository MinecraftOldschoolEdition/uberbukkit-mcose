package net.minecraft.world.scores;

import net.minecraft.server.scoreboard.ModernScoreboard;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

public class PlayerTeam extends Team {
    private final Scoreboard scoreboard;
    final ModernScoreboard.Team handle;
    PlayerTeam(Scoreboard scoreboard, ModernScoreboard.Team handle) { this.scoreboard = scoreboard; this.handle = handle; }
    public Scoreboard getScoreboard() { return this.scoreboard; }
    public String getName() { return this.handle.name; }
    public String getDisplayName() { return this.handle.displayName; }
    public String getFormattedDisplayName() { return "[" + this.handle.displayName + "]"; }
    public void setDisplayName(String value) { this.handle.displayName = value; changed(); }
    public void setPlayerPrefix(String value) { this.handle.prefix = value == null ? "" : value; changed(); }
    public String getPlayerPrefix() { return this.handle.prefix; }
    public void setPlayerSuffix(String value) { this.handle.suffix = value == null ? "" : value; changed(); }
    public String getPlayerSuffix() { return this.handle.suffix; }
    public Collection<String> getPlayers() { return Collections.unmodifiableSet(this.handle.players); }
    public String getFormattedName(String memberName) {
        String color = this.handle.color < 0 ? "" : TeamColor.byId(this.handle.color).legacyPrefix();
        return color + this.handle.prefix + memberName + this.handle.suffix + (color.length() == 0 ? "" : "\u00a7r");
    }
    public static String formatNameForTeam(Team team, String name) { return team == null ? name : team.getFormattedName(name); }
    public boolean isAllowFriendlyFire() { return this.handle.friendlyFire; }
    public void setAllowFriendlyFire(boolean value) { this.handle.friendlyFire = value; changed(); }
    public boolean canSeeFriendlyInvisibles() { return this.handle.seeFriendlyInvisibles; }
    public void setSeeFriendlyInvisibles(boolean value) { this.handle.seeFriendlyInvisibles = value; changed(); }
    public Visibility getNameTagVisibility() { return Visibility.byName(this.handle.nameTagVisibility); }
    public void setNameTagVisibility(Visibility value) { this.handle.nameTagVisibility = value.getSerializedName(); changed(); }
    public Visibility getDeathMessageVisibility() { return Visibility.byName(this.handle.deathMessageVisibility); }
    public void setDeathMessageVisibility(Visibility value) { this.handle.deathMessageVisibility = value.getSerializedName(); changed(); }
    public CollisionRule getCollisionRule() { return CollisionRule.byName(this.handle.collisionRule); }
    public void setCollisionRule(CollisionRule value) { this.handle.collisionRule = value.getSerializedName(); changed(); }
    public Optional<TeamColor> getColor() { return Optional.ofNullable(TeamColor.byId(this.handle.color)); }
    public void setColor(Optional<TeamColor> value) {
        TeamColor color = value == null || !value.isPresent() ? null : value.get();
        this.handle.color = color == null ? -1 : color.id(); changed();
    }
    public byte packOptions() { return (byte)((this.handle.friendlyFire ? 1 : 0) | (this.handle.seeFriendlyInvisibles ? 2 : 0)); }
    public void unpackOptions(byte options) {
        this.handle.friendlyFire = (options & 1) != 0;
        this.handle.seeFriendlyInvisibles = (options & 2) != 0;
        changed();
    }
    void changed() { this.scoreboard.delegate().updateTeam(this.handle); }
}
