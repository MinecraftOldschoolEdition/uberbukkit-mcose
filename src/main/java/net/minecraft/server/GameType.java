package net.minecraft.server;

/**
 * Stable game-mode IDs shared by level/player NBT, commands, and the client.
 *
 * <p>Spectator uses modern Minecraft's ID {@code 3}. UberBukkit keeps the
 * existing MCOSE hardcore compatibility mode at ID {@code 2}.</p>
 */
public enum GameType {
    SURVIVAL(0, "survival"),
    CREATIVE(1, "creative"),
    HARDCORE(2, "hardcore"),
    SPECTATOR(3, "spectator");

    private static final String[] COMMAND_SUGGESTIONS = new String[] {
        "survival", "creative", "hardcore", "spectator",
        "s", "c", "h", "sp", "0", "1", "2", "3"
    };
    private final int id;
    private final String name;

    GameType(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public boolean isCreative() {
        return this == CREATIVE;
    }

    public boolean isSpectator() {
        return this == SPECTATOR;
    }

    public boolean canInteract() {
        return this != SPECTATOR;
    }

    public static GameType byId(int id) {
        for (GameType gameType : values()) {
            if (gameType.id == id) {
                return gameType;
            }
        }
        return SURVIVAL;
    }

    public static GameType byName(String name) {
        if (name != null) {
            for (GameType gameType : values()) {
                if (gameType.name.equalsIgnoreCase(name)) {
                    return gameType;
                }
            }
        }
        return SURVIVAL;
    }

    public static int parse(String token) {
        String value = token == null ? "" : token.toLowerCase();
        if ("survival".equals(value) || "s".equals(value) || "0".equals(value)) {
            return SURVIVAL.id;
        }
        if ("creative".equals(value) || "c".equals(value) || "1".equals(value)) {
            return CREATIVE.id;
        }
        if ("hardcore".equals(value) || "h".equals(value) || "2".equals(value)) {
            return HARDCORE.id;
        }
        if ("spectator".equals(value) || "sp".equals(value) || "3".equals(value)) {
            return SPECTATOR.id;
        }
        return -1;
    }

    public static boolean isValidId(int id) {
        return id >= SURVIVAL.id && id <= SPECTATOR.id;
    }

    public static String[] getCommandSuggestions() {
        return COMMAND_SUGGESTIONS.clone();
    }
}
