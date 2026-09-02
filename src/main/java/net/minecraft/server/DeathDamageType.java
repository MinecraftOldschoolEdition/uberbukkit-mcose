package net.minecraft.server;

import net.minecraft.server.registry.DamageTypeRegistryBootstrap;
import net.minecraft.server.util.ResourceLocation;

public enum DeathDamageType {
    GENERIC("generic", DeathMessageType.DEFAULT, false, DamageTypes.GENERIC),
    GENERIC_KILL("genericKill", DeathMessageType.DEFAULT, false, null),
    PLAYER("player", DeathMessageType.DEFAULT, false, DamageTypes.PLAYER_ATTACK),
    MOB("mob", DeathMessageType.DEFAULT, false, DamageTypes.MOB_ATTACK),
    ARROW("arrow", DeathMessageType.DEFAULT, false, DamageTypes.ARROW),
    THROWN("thrown", DeathMessageType.DEFAULT, false, null),
    FIREBALL("fireball", DeathMessageType.DEFAULT, false, DamageTypes.FIREBALL),
    IN_FIRE("inFire", DeathMessageType.DEFAULT, false, DamageTypes.IN_FIRE),
    ON_FIRE("onFire", DeathMessageType.DEFAULT, false, DamageTypes.ON_FIRE),
    LAVA("lava", DeathMessageType.DEFAULT, false, DamageTypes.LAVA),
    DROWN("drown", DeathMessageType.DEFAULT, false, DamageTypes.DROWN),
    IN_WALL("inWall", DeathMessageType.DEFAULT, false, DamageTypes.IN_WALL),
    CACTUS("cactus", DeathMessageType.DEFAULT, false, DamageTypes.CACTUS),
    FALL("fall", DeathMessageType.FALL_VARIANTS, true, DamageTypes.FALL),
    OUT_OF_WORLD("outOfWorld", DeathMessageType.DEFAULT, false, DamageTypes.OUT_OF_WORLD),
    LIGHTNING_BOLT("lightningBolt", DeathMessageType.DEFAULT, false, DamageTypes.LIGHTNING_BOLT),
    EXPLOSION("explosion", DeathMessageType.DEFAULT, false, DamageTypes.EXPLOSION),
    BAD_RESPAWN_POINT("badRespawnPoint", DeathMessageType.INTENTIONAL_GAME_DESIGN, false, null),
    SUICIDE("suicide", DeathMessageType.DEFAULT, false, null);

    private final String messageId;
    private final DeathMessageType messageType;
    private final boolean fall;
    private final ResourceLocation registryKey;

    DeathDamageType(
            String messageId,
            DeathMessageType messageType,
            boolean fall,
            ResourceLocation registryKey) {
        this.messageId = messageId;
        this.messageType = messageType;
        this.fall = fall;
        this.registryKey = registryKey;
    }

    public String getMessageId() {
        DamageType definition = this.definition();
        return definition == null ? this.messageId : definition.getMessageId();
    }

    public DeathMessageType getMessageType() {
        DamageType definition = this.definition();
        return definition == null
                ? this.messageType : definition.getDeathMessageType();
    }

    public boolean isFall() {
        return this.fall;
    }

    public ResourceLocation getRegistryKey() {
        return this.registryKey;
    }

    private DamageType definition() {
        return this.registryKey == null ? null
                : DamageTypeRegistryBootstrap.get(this.registryKey);
    }
}
