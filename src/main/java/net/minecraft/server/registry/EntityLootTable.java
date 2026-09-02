package net.minecraft.server.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.server.EntityLiving;
import net.minecraft.server.EntityPig;
import net.minecraft.server.EntitySheep;
import net.minecraft.server.EntitySlime;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStack;
import net.minecraft.server.util.ResourceLocation;
import uk.betacraft.uberbukkit.UberbukkitConfig;

/**
 * The strict, legacy-compatible subset of a modern {@code minecraft:entity}
 * loot table. Entity entries are evaluated in declaration order and never use
 * weighted-selection randomness.
 */
public final class EntityLootTable extends LootTable {
    private final List<EntityPool> entityPools = new ArrayList<EntityPool>();

    public EntityLootTable(ResourceLocation id) {
        super(id);
    }

    public EntityLootTable addEntityPool(EntityPool pool) {
        if (pool == null) {
            throw new IllegalArgumentException("Entity loot pool is required");
        }
        entityPools.add(pool);
        return this;
    }

    public List<EntityPool> getEntityPools() {
        return Collections.unmodifiableList(entityPools);
    }

    /** Generates one one-count stack for each legacy EntityItem drop. */
    public List<ItemStack> generateEntityLoot(EntityLiving entity, Random random) {
        if (entity == null || random == null) {
            throw new IllegalArgumentException("Entity and random source are required");
        }
        List<ItemStack> result = new ArrayList<ItemStack>();
        for (int i = 0; i < entityPools.size(); i++) {
            entityPools.get(i).generate(entity, random, result);
        }
        return result;
    }

    public static final class EntityPool {
        private final EntityCondition condition;
        private final EntityEntry entry;

        public EntityPool(EntityCondition condition, EntityEntry entry) {
            if (condition == null || entry == null) {
                throw new IllegalArgumentException("Entity loot pool condition and entry are required");
            }
            this.condition = condition;
            this.entry = entry;
        }

        public EntityCondition getCondition() {
            return condition;
        }

        public EntityEntry getEntry() {
            return entry;
        }

        private void generate(EntityLiving entity, Random random, List<ItemStack> result) {
            if (condition.matches(entity)) {
                entry.generate(entity, random, result);
            }
        }
    }

    public abstract static class EntityEntry {
        private final ResourceLocation type;
        private final EntityCondition condition;

        protected EntityEntry(ResourceLocation type, EntityCondition condition) {
            this.type = type;
            this.condition = condition == null ? EntityCondition.ALWAYS : condition;
        }

        public ResourceLocation getType() {
            return type;
        }

        public EntityCondition getCondition() {
            return condition;
        }

        final boolean generate(EntityLiving entity, Random random, List<ItemStack> result) {
            if (!condition.matches(entity)) {
                return false;
            }
            generateMatched(entity, random, result);
            return true;
        }

        protected abstract void generateMatched(
                EntityLiving entity,
                Random random,
                List<ItemStack> result);
    }

    public static final class ItemEntry extends EntityEntry {
        private static final ResourceLocation TYPE =
                new ResourceLocation("minecraft", "item");

        private final ResourceLocation itemId;
        private final EntityCountProvider count;
        private final int legacyMetadata;

        public ItemEntry(
                EntityCondition condition,
                ResourceLocation itemId,
                EntityCountProvider count,
                int legacyMetadata) {
            super(TYPE, condition);
            if (itemId == null || count == null) {
                throw new IllegalArgumentException("Entity item id and count are required");
            }
            this.itemId = itemId;
            this.count = count;
            this.legacyMetadata = legacyMetadata;
        }

        public ResourceLocation getItemId() {
            return itemId;
        }

        public EntityCountProvider getCount() {
            return count;
        }

        public int getLegacyMetadata() {
            return legacyMetadata;
        }

        protected void generateMatched(
                EntityLiving entity,
                Random random,
                List<ItemStack> result) {
            Item item = ItemRegistry.get(itemId);
            if (item == null) {
                Integer legacyId = ItemRegistry.getId(itemId.toString());
                if (legacyId != null
                        && legacyId.intValue() >= 0
                        && legacyId.intValue() < Item.byId.length) {
                    item = Item.byId[legacyId.intValue()];
                }
            }
            if (item == null) {
                return;
            }

            int amount = count.sample(random);
            for (int i = 0; i < amount; i++) {
                result.add(new ItemStack(item, 1, legacyMetadata));
            }
        }
    }

    public static final class AlternativesEntry extends EntityEntry {
        private static final ResourceLocation TYPE =
                new ResourceLocation("minecraft", "alternatives");

        private final List<EntityEntry> children;

        public AlternativesEntry(EntityCondition condition, List<EntityEntry> children) {
            super(TYPE, condition);
            if (children == null || children.isEmpty()) {
                throw new IllegalArgumentException("Entity alternatives children are required");
            }
            this.children = Collections.unmodifiableList(
                    new ArrayList<EntityEntry>(children));
        }

        public List<EntityEntry> getChildren() {
            return children;
        }

        protected void generateMatched(
                EntityLiving entity,
                Random random,
                List<ItemStack> result) {
            for (int i = 0; i < children.size(); i++) {
                if (children.get(i).generate(entity, random, result)) {
                    return;
                }
            }
        }
    }

    public static final class EntityCountProvider {
        private static final ResourceLocation CONSTANT =
                new ResourceLocation("minecraft", "constant");
        private static final ResourceLocation UNIFORM =
                new ResourceLocation("minecraft", "uniform");

        private final ResourceLocation type;
        private final int minimum;
        private final int maximum;

        public EntityCountProvider(int minimum, int maximum) {
            if (minimum < 0 || maximum < minimum) {
                throw new IllegalArgumentException("Invalid entity loot count range");
            }
            this.type = minimum == maximum ? CONSTANT : UNIFORM;
            this.minimum = minimum;
            this.maximum = maximum;
        }

        public ResourceLocation getType() {
            return type;
        }

        public int getMinimum() {
            return minimum;
        }

        public int getMaximum() {
            return maximum;
        }

        public int sample(Random random) {
            if (minimum == maximum) {
                return minimum;
            }
            return minimum + random.nextInt(maximum - minimum + 1);
        }
    }

    public static final class EntityCondition {
        public static final EntityCondition ALWAYS =
                new EntityCondition(null, null, null, null);

        private final Boolean onFire;
        private final Integer cubeSize;
        private final Boolean sheepSheared;
        private final Integer sheepColor;

        public EntityCondition(
                Boolean onFire,
                Integer cubeSize,
                Boolean sheepSheared,
                Integer sheepColor) {
            this.onFire = onFire;
            this.cubeSize = cubeSize;
            this.sheepSheared = sheepSheared;
            this.sheepColor = sheepColor;
        }

        public Boolean getOnFire() {
            return onFire;
        }

        public Integer getCubeSize() {
            return cubeSize;
        }

        public Boolean getSheepSheared() {
            return sheepSheared;
        }

        public Integer getSheepColor() {
            return sheepColor;
        }

        public boolean isAlways() {
            return onFire == null
                    && cubeSize == null
                    && sheepSheared == null
                    && sheepColor == null;
        }

        public boolean matches(EntityLiving entity) {
            if (entity == null) return false;

            if (onFire != null) {
                boolean burning = entity.fireTicks > 0;
                if (entity instanceof EntityPig
                        && !UberbukkitConfig.getInstance().getBoolean(
                                "mechanics.burning_pig_drop_cooked_meat", true)) {
                    burning = false;
                }
                if (onFire.booleanValue() != burning) return false;
            }

            if (cubeSize != null) {
                if (!(entity instanceof EntitySlime)
                        || ((EntitySlime)entity).getSize() != cubeSize.intValue()) {
                    return false;
                }
            }

            if (sheepSheared != null || sheepColor != null) {
                if (!(entity instanceof EntitySheep)) return false;
                EntitySheep sheep = (EntitySheep)entity;
                if (sheepSheared != null
                        && sheep.isSheared() != sheepSheared.booleanValue()) {
                    return false;
                }
                if (sheepColor != null && sheep.getColor() != sheepColor.intValue()) {
                    return false;
                }
            }
            return true;
        }
    }
}
