package net.minecraft.world.scores;

public interface ScoreHolder {
    String WILDCARD_NAME = "*";
    ScoreHolder WILDCARD = new ScoreHolder() {
        public String getScoreboardName() { return WILDCARD_NAME; }
        public String toString() { return WILDCARD_NAME; }
    };

    String getScoreboardName();
    default String getDisplayName() { return null; }
    default String getFeedbackDisplayName() {
        String display = getDisplayName();
        return display == null ? getScoreboardName() : display;
    }

    static ScoreHolder forNameOnly(final String name) {
        if(WILDCARD_NAME.equals(name)) return WILDCARD;
        return new ScoreHolder() {
            public String getScoreboardName() { return name; }
            public String getFeedbackDisplayName() { return name; }
            public String toString() { return name; }
        };
    }
}
