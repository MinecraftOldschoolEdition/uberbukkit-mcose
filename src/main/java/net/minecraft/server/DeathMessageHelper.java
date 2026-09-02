package net.minecraft.server;

import net.minecraft.server.registry.EntityTypeRegistry;
import net.minecraft.server.util.ResourceLocation;

/**
 * Renders player death messages from structured combat sources.
 *
 * Modern Minecraft keeps the death message decision on the server-side damage
 * source and combat tracker instead of rebuilding it from a flat "last cause"
 * value when the player finally dies. This class is the formatting half of that
 * model for the legacy chat protocol.
 */
public final class DeathMessageHelper {
    private static final String WHITE = "\u00A7f";

    private DeathMessageHelper() {
    }

    public static String getDeathMessage(EntityPlayer player, Entity ignoredLegacyKiller) {
        return player.getCombatTracker().getDeathMessage();
    }

    static String formatGeneric(EntityLiving victim) {
        DeathMessageRule rule = DeathMessageRules.get(DamageTypes.GENERIC);
        return formatTemplate(
                rule.ordinary, getDisplayName(victim), null, null);
    }

    static String format(EntityLiving victim, DeathDamageSource source, EntityLiving killCredit) {
        if (source == null) {
            return formatGeneric(victim);
        }

        DeathDamageType type = source.getType();
        if (type.getRegistryKey() != null) {
            DeathMessageRule rule = DeathMessageRules.get(type.getRegistryKey());
            Entity actor = source.getCausingEntity() != null
                    ? source.getCausingEntity() : source.getDirectEntity();
            String actorName = getEntityDisplayName(actor);
            if (isEmpty(actorName)) {
                actorName = source.getCausingEntityName();
            }
            if (isEmpty(actorName) && killCredit != null) {
                actor = killCredit;
                actorName = getDisplayName(killCredit);
            }
            return formatTemplate(
                    rule.ordinary, getDisplayName(victim), actorName, actor);
        }

        return formatLegacy(victim, source, killCredit);
    }

    private static String formatLegacy(
            EntityLiving victim,
            DeathDamageSource source,
            EntityLiving killCredit) {
        DeathDamageType type = source.getType();
        String deathMsg = "death.attack." + type.getMessageId();
        String victimName = getDisplayName(victim);

        if (type.getMessageType() == DeathMessageType.INTENTIONAL_GAME_DESIGN) {
            String link = "[" + translate(deathMsg + ".link") + "]";
            return WHITE + translate(deathMsg + ".message", victimName, link);
        }

        if (source.getCausingEntity() == null
                && source.getDirectEntity() == null
                && isEmpty(source.getCausingEntityName())) {
            if (killCredit != null) {
                return WHITE + translate(deathMsg + ".player", victimName, getDisplayName(killCredit));
            }
            return WHITE + translate(deathMsg, victimName);
        }

        String sourceName = getSourceDisplayName(source);
        String itemName = getCustomWeaponName(source.getCausingEntity());
        if (!isEmpty(itemName)) {
            return WHITE + translate(deathMsg + ".item", victimName, sourceName, itemName);
        }
        return WHITE + translate(deathMsg, victimName, sourceName);
    }

    static String formatFall(EntityLiving victim, CombatEntry knockOffEntry, Entity killingEntity) {
        DeathDamageSource knockOffSource = knockOffEntry.getSource();
        DeathMessageRule rule = DeathMessageRules.get(DamageTypes.FALL);
        if (rule.fallVariant != null) {
            Entity actor = knockOffSource.getCausingEntity() != null
                    ? knockOffSource.getCausingEntity()
                    : knockOffSource.getDirectEntity();
            String actorName = getEntityDisplayName(actor);
            if (isEmpty(actorName)) {
                actorName = knockOffSource.getCausingEntityName();
            }
            if (isEmpty(actorName) && killingEntity != null) {
                actor = killingEntity;
                actorName = getEntityDisplayName(killingEntity);
            }
            return formatTemplate(
                    rule.fallVariant, getDisplayName(victim), actorName, actor);
        }

        // Unsupported/custom fall sources retain the server's prior fallback.
        if (!knockOffSource.getType().isFall()) {
            String killerName = getEntityDisplayName(killingEntity);
            Entity attackerEntity = knockOffSource.getCausingEntity() != null ? knockOffSource.getCausingEntity() : knockOffSource.getDirectEntity();
            String attackerName = getEntityDisplayName(attackerEntity);
            if (isEmpty(attackerName)) {
                attackerName = knockOffSource.getCausingEntityName();
            }

            if (!isEmpty(attackerName) && !attackerName.equals(killerName)) {
                return formatAssistedFall(victim, attackerEntity, attackerName, "death.fell.assist.item", "death.fell.assist");
            }
            if (!isEmpty(killerName)) {
                return formatAssistedFall(victim, killingEntity, killerName, "death.fell.finish.item", "death.fell.finish");
            }
            return WHITE + translate("death.fell.killer", getDisplayName(victim));
        }

        FallLocation fallLocation = knockOffEntry.getFallLocation() == null ? FallLocation.GENERIC : knockOffEntry.getFallLocation();
        return WHITE + translate(fallLocation.languageKey(), getDisplayName(victim));
    }

    private static String formatAssistedFall(EntityLiving victim, Entity attackerEntity, String attackerName, String withItem, String withoutItem) {
        String itemName = getCustomWeaponName(attackerEntity);
        if (!isEmpty(itemName)) {
            return WHITE + translate(withItem, getDisplayName(victim), attackerName, itemName);
        }
        return WHITE + translate(withoutItem, getDisplayName(victim), attackerName);
    }

    private static String getSourceDisplayName(DeathDamageSource source) {
        if (source.getCausingEntity() != null) {
            return getDisplayName(source.getCausingEntity());
        }
        if (!isEmpty(source.getCausingEntityName())) {
            return source.getCausingEntityName();
        }
        return getDisplayName(source.getDirectEntity());
    }

    private static String getEntityDisplayName(Entity entity) {
        return entity == null ? null : getDisplayName(entity);
    }

    private static String getDisplayName(Entity entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            return !isEmpty(player.displayName) ? player.displayName : player.name;
        }
        if (entity instanceof EntityHuman) {
            return ((EntityHuman) entity).name;
        }
        String customName = entity.getCustomName();
        if (!isEmpty(customName)) {
            return customName;
        }
        if (entity instanceof EntityTNTPrimed) {
            return translate("entity.tnt.name");
        }
        if (entity instanceof EntityArrow) {
            return translate("entity.arrow.name");
        }
        if (entity instanceof EntityFireball) {
            return translate("entity.fireball.name");
        }
        if (entity instanceof EntitySnowball) {
            return translate("entity.snowball.name");
        }
        if (entity instanceof EntityEgg) {
            return translate("entity.egg.name");
        }
        if (entity instanceof EntityFish) {
            return translate("entity.fishing_hook.name");
        }
        if (entity instanceof EntityWeatherStorm) {
            return translate("entity.lightning_bolt.name");
        }
        if (entity instanceof EntityFallingSand) {
            EntityFallingSand falling = (EntityFallingSand) entity;
            if (falling.a > 0 && falling.a < Block.byId.length && Block.byId[falling.a] != null) {
                return Block.byId[falling.a].k();
            }
            return translate("entity.falling_block.name");
        }

        ResourceLocation key = EntityTypeRegistry.getKey(entity.getClass());
        if (key != null) {
            String translated = StatisticCollector.a("entity." + key.getPath() + ".name");
            if (!translated.equals("entity." + key.getPath() + ".name")) {
                return translated;
            }
            return prettifyIdentifier(key.getPath());
        }

        String className = entity.getClass().getSimpleName();
        if (className.startsWith("Entity")) {
            className = className.substring("Entity".length());
        }
        return splitCamelCase(className);
    }

    private static String getCustomWeaponName(Entity entity) {
        ItemPresentation item = getHeldItemPresentation(entity);
        return item != null && item.customNamed ? item.name : null;
    }

    private static ItemPresentation getHeldItemPresentation(Entity entity) {
        if (!(entity instanceof EntityLiving)) {
            return null;
        }
        ItemStack item = getHeldItem((EntityLiving) entity);
        if (item == null || item.id == 0 || item.getItem() == null) {
            return null;
        }
        String customName = item.getPatchedComponents().get(DataComponents.CUSTOM_NAME);
        if (!isEmpty(customName)) {
            return new ItemPresentation(customName, true);
        }
        String translatedName = item.getItem().j();
        return isEmpty(translatedName)
                ? null : new ItemPresentation(translatedName, false);
    }

    private static ItemStack getHeldItem(EntityLiving entity) {
        if (entity instanceof EntityHuman) {
            return ((EntityHuman) entity).G();
        }
        return null;
    }

    private static String prettifyIdentifier(String id) {
        if (id == null || id.length() == 0) {
            return "Unknown";
        }
        StringBuilder out = new StringBuilder();
        boolean upper = true;
        for (int i = 0; i < id.length(); ++i) {
            char c = id.charAt(i);
            if (c == '_' || c == '-' || c == '.') {
                out.append(' ');
                upper = true;
            } else {
                out.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return out.toString();
    }

    private static String splitCamelCase(String value) {
        if (value == null || value.length() == 0) {
            return "Unknown";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); ++i) {
            char c = value.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                out.append(' ');
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String translate(String key, Object... args) {
        try {
            return StatisticCollector.a(key, args);
        } catch (RuntimeException ex) {
            return key;
        }
    }

    private static boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }

    static String formatTemplate(
            DeathMessageRule.Template template,
            String victimName,
            String actorName,
            Entity actor) {
        ItemPresentation item = getHeldItemPresentation(actor);
        DeathMessage message = template.create(
                victimName,
                actorName,
                item == null ? null : item.name,
                item != null && item.customNamed);
        return WHITE + message.format();
    }

    private static final class ItemPresentation {
        private final String name;
        private final boolean customNamed;

        private ItemPresentation(String name, boolean customNamed) {
            this.name = name;
            this.customNamed = customNamed;
        }
    }
}
