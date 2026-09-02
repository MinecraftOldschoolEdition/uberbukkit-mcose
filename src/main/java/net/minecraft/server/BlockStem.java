package net.minecraft.server;

import java.util.Random;
import net.minecraft.server.util.ResourceLocation;

public class BlockStem extends BlockFlower {

	public static final int MAX_AGE = 7;
	private static final int ATTACHED_METADATA_OFFSET = 8;
	private static final int ATTACHED_METADATA_COUNT = 4;
	private static final int[] DIRECTION_X = new int[]{0, 1, 0, -1};
	private static final int[] DIRECTION_Z = new int[]{-1, 0, 1, 0};

    private final Block fruitBlock;

    protected BlockStem(int i, Block block) {
        super(i, 220);
        this.fruitBlock = block;
        this.a(true);
        float f = 2.0F / 16.0F;
        this.a(0.5F - f, 0.0F, 0.5F - f, 0.5F + f, 0.25F, 0.5F + f);
    }

    protected boolean c(int i) {
        return i == Block.SOIL.id;
    }

    public void a(World world, int i, int j, int k, Random random) {
		BlockStateKey state = world.getBlockStateKey(i, j, k);
		if (isAttachedState(state)) {
			return;
		}

        super.a(world, i, j, k, random);
		if (world.getTypeId(i, j, k) != this.id) {
			return;
		}

        if (world.k(i, j, k) >= 9) {
            float f = this.n(world, i, j, k);
            if (random.nextInt(growthChanceBound(f)) == 0) {
				int age = stemAge(world.getBlockStateKey(i, j, k), world.getData(i, j, k));
				if (age < MAX_AGE) {
					world.setBlockStateAndData(i, j, k, this.stemState(age + 1));
                } else {
					this.trySpawnFruit(world, i, j, k, random);
                }
            }
        }
    }

	public boolean d_(World world, int i, int j, int k) {
		BlockStateKey state = world.getBlockStateKey(i, j, k);
		int metadata = world.getData(i, j, k);
		if (!isValidBonemealState(state, metadata)) {
			return false;
		}

		int currentAge = stemAge(state, metadata);
		if (currentAge == MAX_AGE) {
			return this.trySpawnFruitWithBonemeal(world, i, j, k, world.random);
		}

		int age = Math.min(MAX_AGE, currentAge + 2 + world.random.nextInt(4));
		world.setBlockStateAndData(i, j, k, this.stemState(age));
		if (age == MAX_AGE) {
			this.a(world, i, j, k, world.random);
		}
		return true;
    }

	public boolean canGrowWithBonemeal(World world, int i, int j, int k) {
		return isValidBonemealState(world.getBlockStateKey(i, j, k), world.getData(i, j, k));
    }

	private boolean isFruitAt(World world, int i, int j, int k) {
		int blockId = world.getTypeId(i, j, k);
		if (this.fruitBlock == Block.PUMPKIN) {
			return blockId == Block.PUMPKIN_PLAIN.id
					|| blockId == Block.PUMPKIN.id && world.getData(i, j, k) > 3;
		}
		return blockId == this.fruitBlock.id;
	}

	private void trySpawnFruit(World world, int i, int j, int k, Random random) {
		this.trySpawnFruitAtDirection(world, i, j, k, random.nextInt(ATTACHED_METADATA_COUNT));
    }

	private boolean trySpawnFruitWithBonemeal(World world, int i, int j, int k, Random random) {
		int startDirection = random.nextInt(ATTACHED_METADATA_COUNT);
		for (int offset = 0; offset < ATTACHED_METADATA_COUNT; ++offset) {
			int direction = startDirection + offset & 3;
			if (this.trySpawnFruitAtDirection(world, i, j, k, direction)) {
				return true;
			}
		}
		return false;
	}

    private boolean trySpawnFruitAtDirection(World world, int i, int j, int k, int direction) {
		int fruitX = i + DIRECTION_X[direction];
		int fruitZ = k + DIRECTION_Z[direction];

        if (world.getTypeId(fruitX, j, fruitZ) != 0) {
            return false;
        }

        int belowBlockId = world.getTypeId(fruitX, j - 1, fruitZ);
        if (belowBlockId != Block.SOIL.id && belowBlockId != Block.GRASS.id && belowBlockId != Block.DIRT.id) {
            return false;
        }

		String fruitPath = this.fruitBlock == Block.PUMPKIN ? "pumpkin" : "melon";
		boolean fruitPlaced = world.setBlockStateAndData(
			fruitX,
			j,
			fruitZ,
			new BlockStateKey(new ResourceLocation("minecraft", fruitPath))
		);
		if (fruitPlaced) {
			world.setBlockStateAndData(i, j, k, this.attachedStemState(direction));
		}
		return fruitPlaced;
    }

    private float n(World world, int i, int j, int k) {
        float f = 1.0F;
        int l = world.getTypeId(i, j, k - 1);
        int i1 = world.getTypeId(i, j, k + 1);
        int j1 = world.getTypeId(i - 1, j, k);
        int k1 = world.getTypeId(i + 1, j, k);
        int l1 = world.getTypeId(i - 1, j, k - 1);
        int i2 = world.getTypeId(i + 1, j, k - 1);
        int j2 = world.getTypeId(i + 1, j, k + 1);
        int k2 = world.getTypeId(i - 1, j, k + 1);
        boolean flag = j1 == this.id || k1 == this.id;
        boolean flag1 = l == this.id || i1 == this.id;
        boolean flag2 = l1 == this.id || i2 == this.id || j2 == this.id || k2 == this.id;

        for (int l2 = i - 1; l2 <= i + 1; ++l2) {
            for (int i3 = k - 1; i3 <= k + 1; ++i3) {
                int j3 = world.getTypeId(l2, j - 1, i3);
                float f1 = 0.0F;
                if (j3 == Block.SOIL.id) {
                    f1 = 1.0F;
                    if (world.getData(l2, j - 1, i3) > 0) {
                        f1 = 3.0F;
                    }
                }

                if (l2 != i || i3 != k) {
                    f1 /= 4.0F;
                }

                f += f1;
            }
        }

        if (flag2 || flag && flag1) {
            f /= 2.0F;
        }

        return f;
    }

    public int a(int i, int j) {
        return this.textureId;
    }

    public void a(IBlockAccess iblockaccess, int i, int j, int k) {
		int age = getGrowthAge(iblockaccess.getData(i, j, k));
		this.maxY = (double) ((float) (age * 2 + 2) / 16.0F);
        float f = 2.0F / 16.0F;
        this.a(0.5F - f, 0.0F, 0.5F - f, 0.5F + f, (float) this.maxY, 0.5F + f);
    }

    public int c() {
        return 19;
    }

	public void doPhysics(World world, int i, int j, int k, int neighborBlockId) {
		super.doPhysics(world, i, j, k, neighborBlockId);
		if (world.getTypeId(i, j, k) != this.id) {
			return;
		}

		BlockStateKey state = world.getBlockStateKey(i, j, k);
		if (!isAttachedState(state)) {
			return;
		}

		int direction = attachedDirection(state);
		if (!this.isFruitAt(world, i + DIRECTION_X[direction], j, k + DIRECTION_Z[direction])) {
			world.setTypeId(i, j, k, 0);
		}
	}

    public int a(int i, Random random) {
        return -1;
    }

    public int a(Random random) {
        return 1;
    }

	public static int getGrowthAge(int metadata) {
		return Math.min(metadata & 15, MAX_AGE);
	}

	static boolean isAttachedMetadata(int metadata) {
		int safeMetadata = metadata & 15;
		return safeMetadata >= ATTACHED_METADATA_OFFSET
			&& safeMetadata < ATTACHED_METADATA_OFFSET + ATTACHED_METADATA_COUNT;
	}

	static int attachedMetadata(int direction) {
		return ATTACHED_METADATA_OFFSET + (direction & 3);
	}

	static int attachedDirection(int metadata) {
		return (metadata - ATTACHED_METADATA_OFFSET) & 3;
	}

	static String attachedFacing(int metadata) {
		switch (attachedDirection(metadata)) {
			case 1: return "east";
			case 2: return "south";
			case 3: return "west";
			default: return "north";
		}
	}

	static int attachedMetadata(String facing) {
		if ("east".equalsIgnoreCase(facing)) return attachedMetadata(1);
		if ("south".equalsIgnoreCase(facing)) return attachedMetadata(2);
		if ("west".equalsIgnoreCase(facing)) return attachedMetadata(3);
		return attachedMetadata(0);
	}

	static boolean isValidBonemealMetadata(int metadata) {
		return !isAttachedMetadata(metadata);
	}

	static boolean isAttachedState(BlockStateKey state) {
		if (state == null || state.getBlockKey() == null) {
			return false;
		}
		String path = state.getBlockKey().getPath();
		return "attached_pumpkin_stem".equals(path) || "attached_melon_stem".equals(path);
	}

	private static boolean isValidBonemealState(BlockStateKey state, int fallbackMetadata) {
		return !isAttachedState(state) && !isAttachedMetadata(fallbackMetadata);
	}

	private static int stemAge(BlockStateKey state, int fallbackMetadata) {
		String age = state == null ? null : state.getProperty("age");
		if (age != null) {
			try {
				return Math.max(0, Math.min(MAX_AGE, Integer.parseInt(age)));
			} catch (NumberFormatException ignored) {
			}
		}
		return getGrowthAge(fallbackMetadata);
	}

	private static int attachedDirection(BlockStateKey state) {
		return attachedDirection(attachedMetadata(state == null ? null : state.getProperty("facing")));
	}

	private BlockStateKey stemState(int age) {
		String path = this.fruitBlock == Block.PUMPKIN ? "pumpkin_stem" : "melon_stem";
		return new BlockStateKey(new ResourceLocation("minecraft", path))
			.withProperty("age", Integer.toString(Math.max(0, Math.min(MAX_AGE, age))));
	}

	private BlockStateKey attachedStemState(int direction) {
		String path = this.fruitBlock == Block.PUMPKIN ? "attached_pumpkin_stem" : "attached_melon_stem";
		return new BlockStateKey(new ResourceLocation("minecraft", path))
			.withProperty("facing", attachedFacing(attachedMetadata(direction)));
	}

	static int growthChanceBound(float growthRate) {
		return (int)(25.0F / growthRate) + 1;
	}
}
