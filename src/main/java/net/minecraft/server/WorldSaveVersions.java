package net.minecraft.server;

/**
 * World save-version constants for explicit McRegion generations.
 */
public final class WorldSaveVersions {
    public static final int LEGACY_PRE_MCREGION = 0;
    public static final int MCREGION_1 = 19132;
    /** First RegionCore generation. Retained as a source-compatibility alias. */
    public static final int REGIONCORE_1 = 19133;
    public static final int MCREGION_2 = REGIONCORE_1;
    /** Canonical namespaced palette generation. */
    public static final int REGIONCORE_2 = 19134;

    private WorldSaveVersions() {}

    public static int currentWriteVersion() {
        return REGIONCORE_2;
    }

    public static boolean isLegacyPreMcRegion(int version) {
        return version == LEGACY_PRE_MCREGION;
    }

    public static boolean isMcRegion1(int version) {
        return version == MCREGION_1;
    }

    public static boolean requiresRegionCoreConversion(int version) {
        return version < REGIONCORE_2;
    }

    public static boolean isKnownMcRegionVersion(int version) {
        return version == MCREGION_1 || version == REGIONCORE_1 || version == REGIONCORE_2;
    }

    public static boolean usesStatePalette(int version) {
        return version >= REGIONCORE_1;
    }

    public static String nameOf(int version) {
        if (version == REGIONCORE_2) {
            return "RegionCore 2";
        }
        if (version == REGIONCORE_1) {
            return "RegionCore 1";
        }
        if (version == MCREGION_1) {
            return "McRegion 1";
        }
        return "Legacy";
    }
}
