package net.minecraft.server;

/**
 * Immutable projection of 26.3's data-pack-backed damage type.
 *
 * <p>Scaling, exhaustion, and effects remain registry metadata on this Beta
 * server. Existing damage values, armor rules, exhaustion, and hurt sounds
 * stay owned by the legacy gameplay code.</p>
 */
public final class DamageType {
    private final String messageId;
    private final DamageScaling scaling;
    private final float exhaustion;
    private final DamageEffects effects;
    private final DeathMessageType deathMessageType;

    public DamageType(
            String messageId,
            DamageScaling scaling,
            float exhaustion,
            DamageEffects effects,
            DeathMessageType deathMessageType) {
        if (messageId == null || scaling == null || effects == null
                || deathMessageType == null) {
            throw new IllegalArgumentException("Damage type fields cannot be null");
        }
        if (Float.isNaN(exhaustion) || Float.isInfinite(exhaustion)) {
            throw new IllegalArgumentException("Damage type exhaustion must be finite");
        }
        this.messageId = messageId;
        this.scaling = scaling;
        this.exhaustion = exhaustion;
        this.effects = effects;
        this.deathMessageType = deathMessageType;
    }

    public String getMessageId() {
        return this.messageId;
    }

    public DamageScaling getScaling() {
        return this.scaling;
    }

    public float getExhaustion() {
        return this.exhaustion;
    }

    public DamageEffects getEffects() {
        return this.effects;
    }

    public DeathMessageType getDeathMessageType() {
        return this.deathMessageType;
    }
}
