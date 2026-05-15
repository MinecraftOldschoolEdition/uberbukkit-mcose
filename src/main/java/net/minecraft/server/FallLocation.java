package net.minecraft.server;

public final class FallLocation {
    public static final FallLocation GENERIC = new FallLocation("generic");
    public static final FallLocation LADDER = new FallLocation("ladder");
    public static final FallLocation WATER = new FallLocation("water");

    private final String id;

    private FallLocation(String id) {
        this.id = id;
    }

    public static FallLocation getCurrentFallLocation(EntityLiving entity) {
        if (entity == null) {
            return null;
        }
        if (entity.p()) {
            return LADDER;
        }
        if (entity.a(Material.WATER) || entity.f_()) {
            return WATER;
        }
        return null;
    }

    public String languageKey() {
        return "death.fell.accident." + this.id;
    }
}
