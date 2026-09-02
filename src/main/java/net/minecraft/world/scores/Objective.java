package net.minecraft.world.scores;

import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.server.scoreboard.ModernScoreboard;

public class Objective {
    private final Scoreboard scoreboard;
    final ModernScoreboard.Objective handle;
    Objective(Scoreboard scoreboard, ModernScoreboard.Objective handle) { this.scoreboard = scoreboard; this.handle = handle; }
    public Scoreboard getScoreboard() { return this.scoreboard; }
    public String getName() { return this.handle.name; }
    public ObjectiveCriteria getCriteria() {
        return ObjectiveCriteria.byName(this.handle.criteria).orElse(ObjectiveCriteria.DUMMY);
    }
    public String getDisplayName() { return this.handle.displayName; }
    public String getFormattedDisplayName() { return "[" + this.handle.displayName + "]"; }
    public void setDisplayName(String name) {
        this.scoreboard.delegate().updateObjective(this.handle, name, null, null, null);
    }
    public ObjectiveCriteria.RenderType getRenderType() {
        return ObjectiveCriteria.RenderType.byId(this.handle.renderType.id);
    }
    public void setRenderType(ObjectiveCriteria.RenderType type) {
        this.scoreboard.delegate().updateObjective(this.handle, null,
            ModernScoreboard.RenderType.byName(type == null ? "integer" : type.getId()), null, null);
    }
    public boolean displayAutoUpdate() { return this.handle.displayAutoUpdate; }
    public void setDisplayAutoUpdate(boolean value) {
        this.scoreboard.delegate().updateObjective(this.handle, null, null, Boolean.valueOf(value), null);
    }
    public NumberFormat numberFormat() { return Scoreboard.fromInternalFormat(this.handle.numberFormat); }
    public NumberFormat numberFormatOrDefault(NumberFormat fallback) {
        NumberFormat result = numberFormat(); return result == null ? fallback : result;
    }
    public void setNumberFormat(NumberFormat format) {
        this.scoreboard.delegate().setObjectiveNumberFormat(this.handle, Scoreboard.toInternalFormat(format));
    }
}
