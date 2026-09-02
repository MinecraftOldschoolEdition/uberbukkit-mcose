package net.minecraft.world.scores;

import java.util.Collection;
import java.util.Optional;

public abstract class Team {
    public boolean isAlliedTo(Team other) { return other != null && this == other; }
    public abstract String getName();
    public abstract String getFormattedName(String memberName);
    public abstract boolean canSeeFriendlyInvisibles();
    public abstract boolean isAllowFriendlyFire();
    public abstract Visibility getNameTagVisibility();
    public abstract Optional<TeamColor> getColor();
    public abstract Collection<String> getPlayers();
    public abstract Visibility getDeathMessageVisibility();
    public abstract CollisionRule getCollisionRule();

    public enum CollisionRule {
        ALWAYS("always", 0), NEVER("never", 1), PUSH_OTHER_TEAMS("pushOtherTeams", 2), PUSH_OWN_TEAM("pushOwnTeam", 3);
        public final String name; public final int id;
        CollisionRule(String name, int id) { this.name = name; this.id = id; }
        public String getSerializedName() { return this.name; }
        public static CollisionRule byName(String value) {
            for(CollisionRule rule : values()) if(rule.name.equalsIgnoreCase(value)) return rule;
            return ALWAYS;
        }
    }

    public enum Visibility {
        ALWAYS("always", 0), NEVER("never", 1), HIDE_FOR_OTHER_TEAMS("hideForOtherTeams", 2), HIDE_FOR_OWN_TEAM("hideForOwnTeam", 3);
        public final String name; public final int id;
        Visibility(String name, int id) { this.name = name; this.id = id; }
        public String getSerializedName() { return this.name; }
        public static Visibility byName(String value) {
            for(Visibility visibility : values()) if(visibility.name.equalsIgnoreCase(value)) return visibility;
            return ALWAYS;
        }
    }
}
