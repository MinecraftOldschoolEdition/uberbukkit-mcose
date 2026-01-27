package net.minecraft.server;

/**
 * Central version information for Minecraft Oldschool Edition server.
 * This MUST match the client's ModVersion to prevent version mismatches.
 */
public class ModVersion {
    /** The current mod version number (e.g., "1.4", "1.5", "2.0") */
    public static final String VERSION = "1.7.1";
    
    /** The full mod name */
    public static final String MOD_NAME = "Minecraft Oldschool Edition";
    
    /** Full display string combining name and version */
    public static final String FULL_NAME = MOD_NAME + " " + VERSION;
    
    /**
     * Parse the major version number from a version string.
     * @param version Version string like "1.7" or "1.7.2"
     * @return Major version (e.g., 1)
     */
    public static int getMajor(String version) {
        if (version == null || version.isEmpty()) return 0;
        try {
            String[] parts = version.split("\\.");
            return Integer.parseInt(parts[0]);
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * Parse the minor version number from a version string.
     * @param version Version string like "1.7" or "1.7.2"
     * @return Minor version (e.g., 7)
     */
    public static int getMinor(String version) {
        if (version == null || version.isEmpty()) return 0;
        try {
            String[] parts = version.split("\\.");
            if (parts.length >= 2) {
                return Integer.parseInt(parts[1]);
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * Check if the client version is compatible with the server version.
     * Client must be at least the same major.minor version as server.
     * @param clientVersion The client's version string
     * @return true if compatible, false if outdated
     */
    public static boolean isCompatible(String clientVersion) {
        int serverMajor = getMajor(VERSION);
        int serverMinor = getMinor(VERSION);
        int clientMajor = getMajor(clientVersion);
        int clientMinor = getMinor(clientVersion);
        
        // Client major must match or exceed server major
        if (clientMajor < serverMajor) return false;
        if (clientMajor > serverMajor) return true;
        
        // Same major, check minor
        return clientMinor >= serverMinor;
    }
    
    /**
     * Get a user-friendly message for version mismatch.
     */
    public static String getOutdatedMessage(String clientVersion) {
        return "Outdated client! You have " + clientVersion + ", server requires " + VERSION;
    }
}
