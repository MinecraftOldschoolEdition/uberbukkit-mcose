package net.minecraft.server;

import java.util.ArrayList;
import java.util.List;

public final class CombatTracker {
    public static final int RESET_DAMAGE_STATUS_TIME = 100;
    public static final int RESET_COMBAT_STATUS_TIME = 300;

    private final List<CombatEntry> entries = new ArrayList<CombatEntry>();
    private final EntityLiving mob;
    private int lastDamageTime;
    private int combatStartTime;
    private int combatEndTime;
    private boolean inCombat;
    private boolean takingDamage;

    public CombatTracker(EntityLiving mob) {
        this.mob = mob;
    }

    public void recordDamage(DeathDamageSource source, int damage) {
        if (source == null || damage <= 0) {
            return;
        }

        this.recheckStatusBeforeNewDamage();
        this.entries.add(new CombatEntry(source, damage, FallLocation.getCurrentFallLocation(this.mob), this.mob.fallDistance));
        this.lastDamageTime = this.mob.ticksLived;
        this.takingDamage = true;

        if (!this.inCombat && this.mob.health > 0 && shouldEnterCombat(source)) {
            this.inCombat = true;
            this.combatStartTime = this.mob.ticksLived;
            this.combatEndTime = this.combatStartTime;
        }
    }

    private void recheckStatusBeforeNewDamage() {
        int reset = this.inCombat ? RESET_COMBAT_STATUS_TIME : RESET_DAMAGE_STATUS_TIME;
        if (this.takingDamage && this.mob.health > 0 && this.mob.ticksLived - this.lastDamageTime > reset) {
            this.clearStatus();
        }
    }

    private static boolean shouldEnterCombat(DeathDamageSource source) {
        Entity entity = source.getCausingEntity();
        if (entity == null) {
            entity = source.getDirectEntity();
        }
        return entity instanceof EntityLiving;
    }

    public String getDeathMessage() {
        if (this.entries.isEmpty()) {
            return DeathMessageHelper.formatGeneric(this.mob);
        }

        CombatEntry killingBlow = this.entries.get(this.entries.size() - 1);
        DeathDamageSource killingSource = killingBlow.getSource();
        CombatEntry fallEntry = this.getMostSignificantFall();

        if (killingSource.getType().getMessageType() == DeathMessageType.FALL_VARIANTS && fallEntry != null) {
            return DeathMessageHelper.formatFall(this.mob, fallEntry, killingSource.getCausingEntity());
        }

        return DeathMessageHelper.format(this.mob, killingSource, this.getKillCredit());
    }

    public EntityLiving getKillCredit() {
        for (int i = this.entries.size() - 1; i >= 0; --i) {
            DeathDamageSource source = this.entries.get(i).getSource();
            Entity entity = source.getCausingEntity();
            if (entity == null) {
                entity = source.getDirectEntity();
            }
            if (entity instanceof EntityLiving && entity != this.mob) {
                return (EntityLiving) entity;
            }
        }
        return null;
    }

    private CombatEntry getMostSignificantFall() {
        CombatEntry result = null;
        CombatEntry alternative = null;
        int alternativeDamage = 0;
        float bestFall = 0.0F;

        for (int i = 0; i < this.entries.size(); ++i) {
            CombatEntry entry = this.entries.get(i);
            CombatEntry previous = i > 0 ? this.entries.get(i - 1) : null;
            DeathDamageSource source = entry.getSource();

            if (source.getType().isFall() && entry.getFallDistance() > 0.0F && (result == null || entry.getFallDistance() > bestFall)) {
                result = previous != null ? previous : entry;
                bestFall = entry.getFallDistance();
            }

            if (entry.getFallLocation() != null && (alternative == null || entry.getDamage() > alternativeDamage)) {
                alternative = entry;
                alternativeDamage = entry.getDamage();
            }
        }

        if (bestFall > 5.0F && result != null) {
            return result;
        }
        if (alternativeDamage > 5 && alternative != null) {
            return alternative;
        }
        return null;
    }

    public void recheckStatus() {
        int reset = this.inCombat ? RESET_COMBAT_STATUS_TIME : RESET_DAMAGE_STATUS_TIME;
        if (this.takingDamage && (this.mob.health <= 0 || this.mob.ticksLived - this.lastDamageTime > reset)) {
            this.clearStatus();
        }
    }

    private void clearStatus() {
        this.takingDamage = false;
        this.inCombat = false;
        this.combatEndTime = this.mob.ticksLived;
        this.entries.clear();
    }

    public int getCombatDuration() {
        return this.inCombat ? this.mob.ticksLived - this.combatStartTime : this.combatEndTime - this.combatStartTime;
    }
}
