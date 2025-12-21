package net.minecraft.server;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageByProjectileEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

/**
 * Generates death messages like modern Minecraft versions.
 * Messages follow the format: "PlayerName <death_message>"
 * Uses all white text for clean visibility.
 */
public class DeathMessageHelper {
    
    // Color code - all white for clean death messages
    private static final String WHITE = "\u00A7f";
    
    /**
     * Generate a death message for a player.
     * 
     * @param player The player who died
     * @param killer The entity that killed them (can be null)
     * @return The formatted death message
     */
    public static String getDeathMessage(EntityPlayer player, net.minecraft.server.Entity killer) {
        String playerName = player.name;
        EntityDamageEvent lastDamage = player.getBukkitEntity().getLastDamageCause();
        
        // If we have a killer entity, prioritize that
        if (killer != null) {
            org.bukkit.entity.Entity bukkitKiller = killer.getBukkitEntity();
            return getKillMessage(playerName, player, bukkitKiller, lastDamage);
        }
        
        // Otherwise, generate message based on damage cause
        if (lastDamage != null) {
            return getDamageCauseMessage(playerName, player, lastDamage);
        }
        
        // Fallback generic message
        return WHITE + playerName + " died";
    }
    
    /**
     * Check if a player attacker is still valid (within timeout).
     */
    private static EntityPlayer getRecentPlayerAttacker(EntityPlayer victim) {
        if (victim.lastPlayerAttacker != null) {
            long timeSinceAttack = victim.world.getTime() - victim.lastPlayerAttackerTime;
            if (timeSinceAttack >= 0 && timeSinceAttack <= net.minecraft.server.Entity.PLAYER_ATTACKER_TIMEOUT) {
                return victim.lastPlayerAttacker;
            }
        }
        return null;
    }
    
    /**
     * Generate a message when killed by an entity.
     */
    private static String getKillMessage(String playerName, EntityPlayer victim, org.bukkit.entity.Entity killer, EntityDamageEvent lastDamage) {
        String killerName = getEntityName(killer);
        
        // Check if killed by projectile (arrow, etc)
        if (lastDamage instanceof EntityDamageByProjectileEvent) {
            EntityDamageByProjectileEvent projectileEvent = (EntityDamageByProjectileEvent) lastDamage;
            org.bukkit.entity.Entity shooter = projectileEvent.getDamager();
            org.bukkit.entity.Entity projectile = projectileEvent.getProjectile();
            
            if (shooter instanceof Player) {
                // Player shot by another player with arrow
                return WHITE + playerName + " was shot by " + ((Player) shooter).getName();
            } else if (projectile != null) {
                String projectileType = getProjectileName(projectile);
                if (shooter != null) {
                    return WHITE + playerName + " was shot by " + getEntityName(shooter) + " using " + projectileType;
                }
                return WHITE + playerName + " was shot";
            }
        }
        
        // Check if killed by another player
        if (killer instanceof Player) {
            Player killerPlayer = (Player) killer;
            ItemStack weapon = ((EntityPlayer) ((org.bukkit.craftbukkit.entity.CraftPlayer) killerPlayer).getHandle()).inventory.getItemInHand();
            if (weapon != null && weapon.id != 0) {
                String weaponName = getItemName(weapon);
                return WHITE + playerName + " was slain by " + killerPlayer.getName() + " using [" + weaponName + "]";
            }
            return WHITE + playerName + " was slain by " + killerPlayer.getName();
        }
        
        // Killed by mob or other entity
        return WHITE + playerName + " was slain by " + killerName;
    }
    
    /**
     * Generate message based on damage cause when no killer entity.
     */
    private static String getDamageCauseMessage(String playerName, EntityPlayer victim, EntityDamageEvent event) {
        DamageCause cause = event.getCause();
        
        // Check for entity attacker in the damage event
        if (event instanceof EntityDamageByEntityEvent) {
            org.bukkit.entity.Entity damager = ((EntityDamageByEntityEvent) event).getDamager();
            return getKillMessage(playerName, victim, damager, event);
        }
        
        // Check for recent player attacker for indirect kills (spleef, fire, etc)
        EntityPlayer recentAttacker = getRecentPlayerAttacker(victim);
        
        switch (cause) {
            case FALL:
                // Spleef detection: if a player recently damaged victim and they fell
                if (recentAttacker != null) {
                    return WHITE + playerName + " was doomed to fall by " + recentAttacker.name;
                }
                int fallDamage = event.getDamage();
                if (fallDamage > 10) {
                    return WHITE + playerName + " fell from a high place";
                } else if (fallDamage > 5) {
                    return WHITE + playerName + " hit the ground too hard";
                }
                return WHITE + playerName + " fell to their death";
                
            case DROWNING:
                // Check if pushed into water by player
                if (recentAttacker != null) {
                    return WHITE + playerName + " drowned whilst trying to escape " + recentAttacker.name;
                }
                return WHITE + playerName + " drowned";
                
            case FIRE:
                // Check for player-caused fire (using fireSource or recent attacker)
                if (victim.fireSource != null) {
                    return WHITE + playerName + " was set on fire by " + victim.fireSource.name;
                }
                if (recentAttacker != null) {
                    return WHITE + playerName + " walked into fire whilst fighting " + recentAttacker.name;
                }
                return WHITE + playerName + " went up in flames";
                
            case FIRE_TICK:
                // Burning to death - check who set them on fire
                if (victim.fireSource != null) {
                    return WHITE + playerName + " was burnt to a crisp whilst fighting " + victim.fireSource.name;
                }
                if (recentAttacker != null) {
                    return WHITE + playerName + " was burnt to a crisp whilst fighting " + recentAttacker.name;
                }
                return WHITE + playerName + " burned to death";
                
            case LAVA:
                // Check if pushed into lava by player
                if (recentAttacker != null) {
                    return WHITE + playerName + " tried to swim in lava to escape " + recentAttacker.name;
                }
                return WHITE + playerName + " tried to swim in lava";
                
            case SUFFOCATION:
                return WHITE + playerName + " suffocated in a wall";
                
            case CONTACT:
                // Cactus death
                if (recentAttacker != null) {
                    return WHITE + playerName + " walked into a cactus whilst trying to escape " + recentAttacker.name;
                }
                return WHITE + playerName + " was pricked to death";
                
            case VOID:
                // Spleef into void
                if (recentAttacker != null) {
                    return WHITE + playerName + " didn't want to live in the same world as " + recentAttacker.name;
                }
                return WHITE + playerName + " fell out of the world";
                
            case BLOCK_EXPLOSION:
                return WHITE + playerName + " blew up";
                
            case TNT_EXPLOSION:
            case ENTITY_EXPLOSION:
                // Try to get the exploding entity and its source
                if (event instanceof EntityDamageByEntityEvent) {
                    org.bukkit.entity.Entity exploder = ((EntityDamageByEntityEvent) event).getDamager();
                    if (exploder != null) {
                        net.minecraft.server.Entity nmsExploder = ((org.bukkit.craftbukkit.entity.CraftEntity) exploder).getHandle();
                        // Check if it's TNT with a source (player who lit it)
                        if (nmsExploder instanceof EntityTNTPrimed) {
                            EntityTNTPrimed tnt = (EntityTNTPrimed) nmsExploder;
                            if (tnt.source instanceof EntityPlayer) {
                                return WHITE + playerName + " was blown up by " + ((EntityPlayer) tnt.source).name;
                            }
                        }
                        return WHITE + playerName + " was blown up by " + getEntityName(exploder);
                    }
                }
                return WHITE + playerName + " blew up";
                
            case BED_EXPLOSION:
                return WHITE + playerName + " was killed by [Intentional Game Design]";
                
            case LIGHTNING:
                return WHITE + playerName + " was struck by lightning";
                
            case SUICIDE:
                return WHITE + playerName + " took their own life";
                
            case PROJECTILE:
                if (event instanceof EntityDamageByProjectileEvent) {
                    org.bukkit.entity.Entity shooter = ((EntityDamageByProjectileEvent) event).getDamager();
                    if (shooter instanceof Player) {
                        return WHITE + playerName + " was shot by " + ((Player) shooter).getName();
                    }
                    if (shooter != null) {
                        return WHITE + playerName + " was shot by " + getEntityName(shooter);
                    }
                }
                return WHITE + playerName + " was shot";
                
            case ENTITY_ATTACK:
                if (event instanceof EntityDamageByEntityEvent) {
                    org.bukkit.entity.Entity attacker = ((EntityDamageByEntityEvent) event).getDamager();
                    return WHITE + playerName + " was slain by " + getEntityName(attacker);
                }
                return WHITE + playerName + " was killed";
                
            case CUSTOM:
            default:
                return WHITE + playerName + " died";
        }
    }
    
    /**
     * Get display name for an entity.
     */
    private static String getEntityName(org.bukkit.entity.Entity entity) {
        if (entity == null) return "unknown";
        
        if (entity instanceof Player) {
            return ((Player) entity).getName();
        }
        
        // Get entity type from the entity
        net.minecraft.server.Entity nmsEntity = ((org.bukkit.craftbukkit.entity.CraftEntity) entity).getHandle();
        
        if (nmsEntity instanceof EntityZombie) return "Zombie";
        if (nmsEntity instanceof EntitySkeleton) return "Skeleton";
        if (nmsEntity instanceof EntityCreeper) return "Creeper";
        if (nmsEntity instanceof EntitySpider) return "Spider";
        if (nmsEntity instanceof EntitySlime) return "Slime";
        if (nmsEntity instanceof EntityGhast) return "Ghast";
        if (nmsEntity instanceof EntityPigZombie) return "Zombie Pigman";
        if (nmsEntity instanceof EntityWolf) {
            EntityWolf wolf = (EntityWolf) nmsEntity;
            if (wolf.isTamed()) {
                return "Wolf"; // Could add owner name here
            }
            return "Wolf";
        }
        if (nmsEntity instanceof EntityPig) return "Pig";
        if (nmsEntity instanceof EntityCow) return "Cow";
        if (nmsEntity instanceof EntitySheep) return "Sheep";
        if (nmsEntity instanceof EntityChicken) return "Chicken";
        if (nmsEntity instanceof EntitySquid) return "Squid";
        if (nmsEntity instanceof EntitySnowman) return "Snow Golem";
        if (nmsEntity instanceof EntityHerobrine) return "Herobrine";
        if (nmsEntity instanceof EntityTNTPrimed) return "TNT";
        if (nmsEntity instanceof EntityArrow) return "Arrow";
        if (nmsEntity instanceof EntityFireball) return "Fireball";
        if (nmsEntity instanceof EntityFallingSand) {
            EntityFallingSand sand = (EntityFallingSand) nmsEntity;
            if (sand.a == Block.SAND.id) return "Falling Sand";
            if (sand.a == Block.GRAVEL.id) return "Falling Gravel";
            return "Falling Block";
        }
        if (nmsEntity instanceof EntityMinecart) return "Minecart";
        if (nmsEntity instanceof EntityBoat) return "Boat";
        
        // Fallback to class name
        String className = nmsEntity.getClass().getSimpleName();
        if (className.startsWith("Entity")) {
            className = className.substring(6);
        }
        return className;
    }
    
    /**
     * Get display name for a projectile.
     */
    private static String getProjectileName(org.bukkit.entity.Entity projectile) {
        net.minecraft.server.Entity nmsEntity = ((org.bukkit.craftbukkit.entity.CraftEntity) projectile).getHandle();
        
        if (nmsEntity instanceof EntityArrow) return "an arrow";
        if (nmsEntity instanceof EntitySnowball) return "a snowball";
        if (nmsEntity instanceof EntityEgg) return "an egg";
        if (nmsEntity instanceof EntityFireball) return "a fireball";
        if (nmsEntity instanceof EntityFish) return "a fishing rod";
        
        return "a projectile";
    }
    
    /**
     * Get display name for an item.
     */
    private static String getItemName(ItemStack item) {
        if (item == null || item.id == 0) return "their bare hands";
        
        Item itemType = Item.byId[item.id];
        if (itemType != null) {
            // Try to get the item name
            String name = itemType.j();
            if (name != null && !name.isEmpty()) {
                // Clean up the translation key format
                if (name.startsWith("item.") || name.startsWith("tile.")) {
                    name = name.substring(5);
                }
                if (name.endsWith(".name")) {
                    name = name.substring(0, name.length() - 5);
                }
                // Convert camelCase to readable format
                return formatItemName(name);
            }
        }
        
        return "item #" + item.id;
    }
    
    /**
     * Format item name from internal format to display format.
     */
    private static String formatItemName(String name) {
        // Common item name mappings
        switch (name.toLowerCase()) {
            case "sworddiamond": return "Diamond Sword";
            case "swordgold": return "Golden Sword";
            case "swordiron": return "Iron Sword";
            case "swordstone": return "Stone Sword";
            case "swordwood": return "Wooden Sword";
            case "axediamond": return "Diamond Axe";
            case "axegold": return "Golden Axe";
            case "axeiron": return "Iron Axe";
            case "axestone": return "Stone Axe";
            case "axewood": return "Wooden Axe";
            case "pickaxediamond": return "Diamond Pickaxe";
            case "pickaxegold": return "Golden Pickaxe";
            case "pickaxeiron": return "Iron Pickaxe";
            case "pickaxestone": return "Stone Pickaxe";
            case "pickaxewood": return "Wooden Pickaxe";
            case "hoediamond": return "Diamond Hoe";
            case "hoegold": return "Golden Hoe";
            case "hoeiron": return "Iron Hoe";
            case "hoestone": return "Stone Hoe";
            case "hoewood": return "Wooden Hoe";
            case "shovelwood": return "Wooden Shovel";
            case "shovelstone": return "Stone Shovel";
            case "shoveliron": return "Iron Shovel";
            case "shovelgold": return "Golden Shovel";
            case "shoveldiamond": return "Diamond Shovel";
            case "bow": return "Bow";
            case "arrow": return "Arrow";
            case "fishingrod": return "Fishing Rod";
            case "flintandsteel": return "Flint and Steel";
            case "shears": return "Shears";
            default:
                // Simple formatting: add spaces before capitals
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < name.length(); i++) {
                    char c = name.charAt(i);
                    if (i > 0 && Character.isUpperCase(c)) {
                        sb.append(' ');
                    }
                    sb.append(i == 0 ? Character.toUpperCase(c) : c);
                }
                return sb.toString();
        }
    }
}

