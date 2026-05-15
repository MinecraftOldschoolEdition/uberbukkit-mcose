package net.minecraft.server;

public enum DeathDamageType {
    GENERIC("generic", DeathMessageType.DEFAULT, false),
    GENERIC_KILL("genericKill", DeathMessageType.DEFAULT, false),
    PLAYER("player", DeathMessageType.DEFAULT, false),
    MOB("mob", DeathMessageType.DEFAULT, false),
    ARROW("arrow", DeathMessageType.DEFAULT, false),
    THROWN("thrown", DeathMessageType.DEFAULT, false),
    FIREBALL("fireball", DeathMessageType.DEFAULT, false),
    IN_FIRE("inFire", DeathMessageType.DEFAULT, false),
    ON_FIRE("onFire", DeathMessageType.DEFAULT, false),
    LAVA("lava", DeathMessageType.DEFAULT, false),
    DROWN("drown", DeathMessageType.DEFAULT, false),
    IN_WALL("inWall", DeathMessageType.DEFAULT, false),
    CACTUS("cactus", DeathMessageType.DEFAULT, false),
    FALL("fall", DeathMessageType.FALL_VARIANTS, true),
    OUT_OF_WORLD("outOfWorld", DeathMessageType.DEFAULT, false),
    LIGHTNING_BOLT("lightningBolt", DeathMessageType.DEFAULT, false),
    EXPLOSION("explosion", DeathMessageType.DEFAULT, false),
    BAD_RESPAWN_POINT("badRespawnPoint", DeathMessageType.INTENTIONAL_GAME_DESIGN, false),
    SUICIDE("suicide", DeathMessageType.DEFAULT, false);

    private final String messageId;
    private final DeathMessageType messageType;
    private final boolean fall;

    DeathDamageType(String messageId, DeathMessageType messageType, boolean fall) {
        this.messageId = messageId;
        this.messageType = messageType;
        this.fall = fall;
    }

    public String getMessageId() {
        return this.messageId;
    }

    public DeathMessageType getMessageType() {
        return this.messageType;
    }

    public boolean isFall() {
        return this.fall;
    }
}
