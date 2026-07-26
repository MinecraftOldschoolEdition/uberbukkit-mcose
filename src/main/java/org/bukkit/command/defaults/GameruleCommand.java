package org.bukkit.command.defaults;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import net.minecraft.server.WorldData;
import net.minecraft.server.MinecraftServer;
import java.util.Locale;

public class GameruleCommand extends VanillaCommand {
    
    // Boolean gamerules
    private static final String[] BOOLEAN_RULES = {"doDayNightCycle", "tntexplodes", "mobGriefing", "doWeatherCycle", "doFireTick", "showDeathMessages", "sleepEnabled", "advertiseAchievements", "keepInventory", "punchToPrimeTNT", "punchSheepForWool"};
    // Integer gamerules
    private static final String[] INTEGER_RULES = {"spawnRadius", "spawnProtectionRadius"};
    
    public GameruleCommand() {
        super("gamerule");
        this.description = "Sets or queries a game rule.";
        this.usageMessage = "/gamerule <rule name> [value]";
        this.setPermission("bukkit.command.gamerule");
    }
    
    private boolean isBooleanRule(String name) {
        for (String rule : BOOLEAN_RULES) {
            if (rule.equalsIgnoreCase(name)) return true;
        }
        return false;
    }
    
    private boolean isIntegerRule(String name) {
        for (String rule : INTEGER_RULES) {
            if (rule.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    static String[] booleanRuleNames() {
        return BOOLEAN_RULES.clone();
    }

    static String[] integerRuleNames() {
        return INTEGER_RULES.clone();
    }

    static Boolean getBooleanRuleValue(WorldData worldData, String ruleName) {
        if (worldData == null || ruleName == null) {
            return null;
        }
        if (ruleName.equalsIgnoreCase("doDayNightCycle")) {
            return Boolean.valueOf(worldData.getDoDayNightCycle());
        }
        if (ruleName.equalsIgnoreCase("tntexplodes")) {
            return Boolean.valueOf(worldData.getTntexplodes());
        }
        if (ruleName.equalsIgnoreCase("mobGriefing")) {
            return Boolean.valueOf(worldData.getMobGriefing());
        }
        if (ruleName.equalsIgnoreCase("doWeatherCycle")) {
            return Boolean.valueOf(worldData.getDoWeatherCycle());
        }
        if (ruleName.equalsIgnoreCase("doFireTick")) {
            return Boolean.valueOf(worldData.getDoFireTick());
        }
        if (ruleName.equalsIgnoreCase("showDeathMessages")) {
            return Boolean.valueOf(worldData.getShowDeathMessages());
        }
        if (ruleName.equalsIgnoreCase("sleepEnabled")) {
            return Boolean.valueOf(worldData.getSleepEnabled());
        }
        if (ruleName.equalsIgnoreCase("advertiseAchievements")) {
            return Boolean.valueOf(worldData.getAdvertiseAchievements());
        }
        if (ruleName.equalsIgnoreCase("keepInventory")) {
            return Boolean.valueOf(worldData.getKeepInventory());
        }
        if (ruleName.equalsIgnoreCase("punchToPrimeTNT")) {
            return Boolean.valueOf(worldData.getPunchToPrimeTNT());
        }
        if (ruleName.equalsIgnoreCase("punchSheepForWool")) {
            return Boolean.valueOf(worldData.getPunchSheepForWool());
        }
        return null;
    }

    static boolean setBooleanRuleValue(WorldData worldData, String ruleName, boolean value) {
        if (worldData == null || ruleName == null) {
            return false;
        }
        if (ruleName.equalsIgnoreCase("doDayNightCycle")) {
            worldData.setDoDayNightCycle(value);
        } else if (ruleName.equalsIgnoreCase("tntexplodes")) {
            worldData.setTntexplodes(value);
        } else if (ruleName.equalsIgnoreCase("mobGriefing")) {
            worldData.setMobGriefing(value);
        } else if (ruleName.equalsIgnoreCase("doWeatherCycle")) {
            worldData.setDoWeatherCycle(value);
        } else if (ruleName.equalsIgnoreCase("doFireTick")) {
            worldData.setDoFireTick(value);
        } else if (ruleName.equalsIgnoreCase("showDeathMessages")) {
            worldData.setShowDeathMessages(value);
        } else if (ruleName.equalsIgnoreCase("sleepEnabled")) {
            worldData.setSleepEnabled(value);
        } else if (ruleName.equalsIgnoreCase("advertiseAchievements")) {
            worldData.setAdvertiseAchievements(value);
        } else if (ruleName.equalsIgnoreCase("keepInventory")) {
            worldData.setKeepInventory(value);
        } else if (ruleName.equalsIgnoreCase("punchToPrimeTNT")) {
            worldData.setPunchToPrimeTNT(value);
        } else if (ruleName.equalsIgnoreCase("punchSheepForWool")) {
            worldData.setPunchSheepForWool(value);
        } else {
            return false;
        }
        return true;
    }

    private boolean isSpawnProtectionRule(String name) {
        return name.equalsIgnoreCase("spawnprotectionradius");
    }

    private boolean isRespawnRadiusRule(String name) {
        return name.equalsIgnoreCase("spawnradius");
    }

    private WorldData resolveTargetWorldData(CommandSender sender, MinecraftServer mcServer) {
        if (sender instanceof Player) {
            CraftPlayer craftPlayer = (CraftPlayer) sender;
            if (craftPlayer.getHandle() != null && craftPlayer.getHandle().world != null) {
                return craftPlayer.getHandle().world.worldData;
            }
        }
        if (mcServer.worlds.isEmpty()) {
            return null;
        }
        return mcServer.worlds.get(0).worldData;
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args) {
        if (!testPermission(sender)) {
            return true;
        }

        if (args.length == 0) {
            // List all gamerules
            sender.sendMessage(ChatColor.YELLOW + "Available gamerules:");
            for (String rule : BOOLEAN_RULES) {
                sender.sendMessage("  " + rule + " (boolean)");
            }
            for (String rule : INTEGER_RULES) {
                sender.sendMessage("  " + rule + " (integer)");
            }
            return true;
        }

        MinecraftServer mcServer = ((CraftServer) Bukkit.getServer()).getServer();
        WorldData worldData = resolveTargetWorldData(sender, mcServer);
        if (worldData == null) {
            sender.sendMessage(ChatColor.RED + "Error: Could not retrieve world data for the target world.");
            return true;
        }

        String ruleName = args[0].toLowerCase();

        if (args.length == 1) {
            // Query a gamerule value
            if (isBooleanRule(ruleName)) {
                Boolean value = getBooleanRuleValue(worldData, ruleName);
                if (value == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown game rule: " + args[0]);
                    return false;
                }
                sender.sendMessage(args[0] + " = " + value.booleanValue());
            } else if (isRespawnRadiusRule(ruleName)) {
                sender.sendMessage(args[0] + " = " + worldData.getSpawnRadius());
            } else if (isSpawnProtectionRule(ruleName)) {
                sender.sendMessage(args[0] + " = " + Bukkit.getServer().getSpawnRadius());
            } else {
                sender.sendMessage(ChatColor.RED + "Unknown game rule: " + args[0]);
                return false;
            }
        } else if (args.length == 2) {
            String valueStr = args[1];
            
            // Handle integer gamerules
            if (isIntegerRule(args[0])) {
                int intValue;
                try {
                    intValue = Integer.parseInt(valueStr);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid value for game rule. Expected an integer.");
                    return false;
                }

                if (isRespawnRadiusRule(ruleName)) {
                    int clampedValue = Math.max(0, intValue);
                    worldData.setSpawnRadius(clampedValue);
                    sender.sendMessage("Game rule " + args[0] + " has been set to " + clampedValue);
                    if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
                        Bukkit.getLogger().info("User " + sender.getName() + " set game rule " + args[0] + " to " + clampedValue);
                    }
                } else if (isSpawnProtectionRule(ruleName)) {
                    int clampedValue = Math.max(0, intValue);
                    Bukkit.getServer().setSpawnRadius(clampedValue);
                    sender.sendMessage("Game rule " + args[0] + " has been set to " + clampedValue);
                    if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
                        Bukkit.getLogger().info("User " + sender.getName() + " set game rule " + args[0] + " to " + clampedValue);
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Unknown game rule: " + args[0]);
                    return false;
                }
            } else if (isBooleanRule(args[0])) {
                // Handle boolean gamerules
                boolean value;
                if (valueStr.equalsIgnoreCase("true")) {
                    value = true;
                } else if (valueStr.equalsIgnoreCase("false")) {
                    value = false;
                } else {
                    sender.sendMessage(ChatColor.RED + "Invalid value for game rule. Use 'true' or 'false'.");
                    return false;
                }

                if (!setBooleanRuleValue(worldData, ruleName, value)) {
                    sender.sendMessage(ChatColor.RED + "Unknown game rule: " + args[0]);
                    return false;
                }
                
                sender.sendMessage("Game rule " + args[0] + " has been set to " + value);
                if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
                    Bukkit.getLogger().info("User " + sender.getName() + " set game rule " + args[0] + " to " + value);
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Unknown game rule: " + args[0]);
                return false;
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: " + usageMessage);
            return false;
        }

        return true;
    }

    @Override
    public boolean matches(String input) {
        if (input == null) return false;
        String lowerInput = input.toLowerCase();
        return lowerInput.startsWith("gamerule ") || lowerInput.equals("gamerule");
    }
    
    @Override
    public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
        java.util.List<String> completions = new java.util.ArrayList<String>();
        if (args.length == 1) {
            String rawPrefix = args[0] == null ? "" : args[0];
            String prefix = rawPrefix.toLowerCase(Locale.ROOT);
            boolean preferLowerCase = rawPrefix.length() > 0 && rawPrefix.equals(rawPrefix.toLowerCase(Locale.ROOT));
            // Add all gamerules
            for (String rule : BOOLEAN_RULES) {
                if (rule.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    completions.add(preferLowerCase ? rule.toLowerCase(Locale.ROOT) : rule);
                }
            }
            for (String rule : INTEGER_RULES) {
                if (rule.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    completions.add(preferLowerCase ? rule.toLowerCase(Locale.ROOT) : rule);
                }
            }
        } else if (args.length == 2) {
            // Suggest true/false for boolean rules, nothing for integer rules
            if (isBooleanRule(args[0])) {
                String prefix = (args[1] == null ? "" : args[1]).toLowerCase(Locale.ROOT);
                String[] values = {"true", "false"};
                for (String val : values) {
                    if (val.startsWith(prefix)) {
                        completions.add(val);
                    }
                }
            }
            // For integer rules, no suggestions (user types a number)
        }
        return completions;
    }
}
