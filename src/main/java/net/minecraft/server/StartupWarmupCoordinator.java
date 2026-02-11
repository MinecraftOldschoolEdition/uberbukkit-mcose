package net.minecraft.server;

import com.legacyminecraft.poseidon.PoseidonConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Performs blocking startup warmup so the server is actually ready when startup finishes.
 * Warmup order uses a spiral from spawn for better first-use locality.
 */
public final class StartupWarmupCoordinator {

    private final MinecraftServer server;
    private final boolean enabled;
    private final int overworldRadiusChunks;
    private final int netherRadiusChunks;
    private final int joinerExtraRadiusChunks;
    private final long progressLogIntervalMs;

    public StartupWarmupCoordinator(MinecraftServer server) {
        this.server = server;
        PoseidonConfig config = PoseidonConfig.getInstance();
        this.enabled = config.getConfigBoolean("settings.startup-readiness.enabled", true);
        this.overworldRadiusChunks = clampMin(getConfigInt(config, "settings.startup-readiness.overworld-radius-chunks", 14), 0);
        this.netherRadiusChunks = clampMin(getConfigInt(config, "settings.startup-readiness.nether-radius-chunks", 8), 0);
        this.joinerExtraRadiusChunks = clampMin(getConfigInt(config, "settings.startup-readiness.joiner-extra-radius-chunks", 4), 0);
        int logIntervalSeconds = clampMin(getConfigInt(config, "settings.startup-readiness.progress-log-interval-seconds", 2), 1);
        this.progressLogIntervalMs = logIntervalSeconds * 1000L;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void warmupWorlds(List<WorldServer> worlds) {
        if (!this.enabled || worlds == null || worlds.isEmpty()) {
            return;
        }

        Map<WorldServer, Integer> worldRadii = new LinkedHashMap<WorldServer, Integer>();
        int totalChunks = 0;

        for (WorldServer world : worlds) {
            int radius = resolveWarmupRadius(world);
            if (radius <= 0) {
                continue;
            }

            worldRadii.put(world, Integer.valueOf(radius));
            int diameter = radius * 2 + 1;
            totalChunks += diameter * diameter;
        }

        if (worldRadii.isEmpty() || totalChunks <= 0) {
            return;
        }

        long startedAt = System.currentTimeMillis();
        long lastLogAt = startedAt;
        int warmedChunks = 0;

        MinecraftServer.log.info("[StartupWarmup] Beginning blocking warmup for " + worldRadii.size() + " world(s), total target " + totalChunks + " chunks.");

        for (Map.Entry<WorldServer, Integer> entry : worldRadii.entrySet()) {
            WorldServer world = entry.getKey();
            int radius = entry.getValue().intValue();
            ChunkCoordinates spawn = world.getSpawn();
            int centerChunkX = spawn.x >> 4;
            int centerChunkZ = spawn.z >> 4;
            List<ChunkCoordIntPair> spiral = buildSpiral(centerChunkX, centerChunkZ, radius);

            MinecraftServer.log.info("[StartupWarmup] Warming world '" + world.worldData.name + "' around spawn chunk (" + centerChunkX + ", " + centerChunkZ + ") radius=" + radius + " chunks.");

            for (int i = 0; i < spiral.size(); i++) {
                if (!MinecraftServer.isRunning(this.server)) {
                    MinecraftServer.log.warning("[StartupWarmup] Server stop detected during warmup.");
                    return;
                }

                ChunkCoordIntPair pair = spiral.get(i);
                world.chunkProviderServer.getChunkAt(pair.x, pair.z);

                while (world.doLighting() && MinecraftServer.isRunning(this.server)) {
                    ;
                }

                warmedChunks++;
                long now = System.currentTimeMillis();
                if (now - lastLogAt >= this.progressLogIntervalMs) {
                    int pct = (int) ((warmedChunks * 100L) / Math.max(1, totalChunks));
                    MinecraftServer.log.info("[StartupWarmup] Progress: " + pct + "% (" + warmedChunks + "/" + totalChunks + " chunks)");
                    lastLogAt = now;
                }
            }
        }

        long elapsedMs = System.currentTimeMillis() - startedAt;
        MinecraftServer.log.info("[StartupWarmup] Completed warmup: " + warmedChunks + " chunks in " + String.format("%.2f", elapsedMs / 1000.0D) + "s.");
    }

    private int resolveWarmupRadius(WorldServer world) {
        if (world == null) {
            return 0;
        }

        if (world.dimension == 0) {
            return this.overworldRadiusChunks + this.joinerExtraRadiusChunks;
        }

        if (world.dimension == -1) {
            return this.netherRadiusChunks;
        }

        return this.overworldRadiusChunks;
    }

    private static List<ChunkCoordIntPair> buildSpiral(int centerX, int centerZ, int radius) {
        List<ChunkCoordIntPair> result = new ArrayList<ChunkCoordIntPair>();
        if (radius < 0) {
            return result;
        }

        int x = centerX;
        int z = centerZ;
        result.add(new ChunkCoordIntPair(x, z));

        int stepLength = 1;
        int direction = 0;

        while (maxDistance(centerX, centerZ, x, z) < radius) {
            for (int repeat = 0; repeat < 2; repeat++) {
                for (int step = 0; step < stepLength; step++) {
                    switch (direction) {
                        case 0:
                            x++;
                            break;
                        case 1:
                            z++;
                            break;
                        case 2:
                            x--;
                            break;
                        default:
                            z--;
                            break;
                    }

                    if (maxDistance(centerX, centerZ, x, z) <= radius) {
                        result.add(new ChunkCoordIntPair(x, z));
                    }
                }
                direction = (direction + 1) & 3;
            }
            stepLength++;
        }

        return result;
    }

    private static int maxDistance(int centerX, int centerZ, int x, int z) {
        int dx = Math.abs(x - centerX);
        int dz = Math.abs(z - centerZ);
        return Math.max(dx, dz);
    }

    private static int clampMin(int value, int min) {
        return value < min ? min : value;
    }

    private static int getConfigInt(PoseidonConfig config, String path, int defaultValue) {
        try {
            Object value = config.getConfigOption(path, Integer.valueOf(defaultValue));
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(String.valueOf(value));
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }
}
