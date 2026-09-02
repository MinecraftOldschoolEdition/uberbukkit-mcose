package net.minecraft.server.registry;

import java.util.Random;
import net.minecraft.server.util.ResourceLocation;

/**
 * Data representation and execution adapter for the shrine's pre-existing
 * per-chunk random-chance placement. Each attempt owns a dedicated
 * {@link Random}; population random state is never observed or advanced.
 */
public final class LegacyRandomChanceStructurePlacement {
    public static final ResourceLocation TYPE = new ResourceLocation(
            "minecraft", "legacy_random_chance");

    private final int chance;
    private final long salt;
    private final long chunkXMultiplier;
    private final long chunkZMultiplier;
    private final int candidateOffset;
    private final int candidateBound;
    private final int generationY;
    private final int locateY;

    public LegacyRandomChanceStructurePlacement(
            int chance,
            long salt,
            long chunkXMultiplier,
            long chunkZMultiplier,
            int candidateOffset,
            int candidateBound,
            int generationY,
            int locateY) {
        if (chance <= 0 || candidateBound <= 0) {
            throw new IllegalArgumentException(
                    "Placement chance and candidate bound must be positive");
        }
        this.chance = chance;
        this.salt = salt;
        this.chunkXMultiplier = chunkXMultiplier;
        this.chunkZMultiplier = chunkZMultiplier;
        this.candidateOffset = candidateOffset;
        this.candidateBound = candidateBound;
        this.generationY = generationY;
        this.locateY = locateY;
    }

    /**
     * Samples one chunk using the precise legacy call order: chance, X, Z.
     * A successful candidate retains that same advanced random source so the
     * structure generator sees exactly the state it saw before this cutover.
     */
    public Candidate sample(long worldSeed, int chunkX, int chunkZ) {
        long placementSeed = (long)chunkX * this.chunkXMultiplier
                + (long)chunkZ * this.chunkZMultiplier
                + worldSeed + this.salt;
        Random random = new Random(placementSeed);
        if (random.nextInt(this.chance) != 0) {
            return null;
        }

        int blockX = chunkX * 16
                + random.nextInt(this.candidateBound)
                + this.candidateOffset;
        int blockZ = chunkZ * 16
                + random.nextInt(this.candidateBound)
                + this.candidateOffset;
        return new Candidate(
                blockX, blockZ, this.generationY, this.locateY, random);
    }

    public int getChance() {
        return this.chance;
    }

    public long getSalt() {
        return this.salt;
    }

    public long getChunkXMultiplier() {
        return this.chunkXMultiplier;
    }

    public long getChunkZMultiplier() {
        return this.chunkZMultiplier;
    }

    public int getCandidateOffset() {
        return this.candidateOffset;
    }

    public int getCandidateBound() {
        return this.candidateBound;
    }

    public int getGenerationY() {
        return this.generationY;
    }

    public int getLocateY() {
        return this.locateY;
    }

    public static final class Candidate {
        private final int blockX;
        private final int blockZ;
        private final int generationY;
        private final int locateY;
        private final Random random;

        private Candidate(
                int blockX,
                int blockZ,
                int generationY,
                int locateY,
                Random random) {
            this.blockX = blockX;
            this.blockZ = blockZ;
            this.generationY = generationY;
            this.locateY = locateY;
            this.random = random;
        }

        public int getBlockX() {
            return this.blockX;
        }

        public int getBlockZ() {
            return this.blockZ;
        }

        public int getGenerationY() {
            return this.generationY;
        }

        public int getLocateY() {
            return this.locateY;
        }

        public Random getRandom() {
            return this.random;
        }

        public int[] toLocatePosition() {
            return new int[] {this.blockX, this.locateY, this.blockZ};
        }
    }
}
