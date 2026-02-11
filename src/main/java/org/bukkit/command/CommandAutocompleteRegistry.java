package org.bukkit.command;

import net.minecraft.server.EntityItem;
import net.minecraft.server.EntityPainting;
import net.minecraft.server.registry.BlockRegistry;
import net.minecraft.server.registry.EntityTypeRegistry;
import net.minecraft.server.registry.ItemRegistry;
import net.minecraft.server.registry.Registries;
import net.minecraft.server.registry.StructureTypes;
import net.minecraft.server.util.ResourceLocation;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.command.defaults.VanillaCommand;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Syntax-aware command autocomplete registry used by packet203 and custom payload command APIs.
 */
public final class CommandAutocompleteRegistry {
    public static final int PROTOCOL_VERSION = 1;
    public static final String CHANNEL_TREE = "MCOSE|CmdTree";
    public static final String CHANNEL_REQUEST = "MCOSE|CmdReq";
    public static final String CHANNEL_RESPONSE = "MCOSE|CmdRes";

    private static final String ARG_PLAYER = "player";
    private static final String ARG_GAMEMODE = "gamemode";
    private static final String ARG_ITEM_KEY = "item_key";
    private static final String ARG_BLOCK_KEY = "block_key";
    private static final String ARG_ENTITY_KEY = "entity_key";
    private static final String ARG_STRUCTURE_KEY = "structure_key";
    private static final String ARG_WEATHER_TYPE = "weather_type";
    private static final String ARG_TIME_ACTION = "time_action";
    private static final String ARG_TIME_VALUE = "time_value";
    private static final String ARG_GAMERULE_NAME = "gamerule_name";
    private static final String ARG_GAMERULE_VALUE = "gamerule_value";
    private static final String ARG_PROFILE_ACTION = "profile_action";
    private static final String ARG_INTEGER = "integer";
    private static final String ARG_COORD = "coordinate";
    private static final String ARG_TEXT = "text";

    private static final String[] BOOLEAN_GAMERULES = new String[] {
        "doDayNightCycle",
        "tntexplodes",
        "mobGriefing",
        "doWeatherCycle",
        "showDeathMessages",
        "sleepEnabled",
        "advertiseAchievements",
        "keepInventory"
    };

    private static final String[] INTEGER_GAMERULES = new String[] { "spawnRadius" };

    private static final String[] GAMEMODE_VALUES = new String[] {
        "survival", "creative", "hardcore", "s", "c", "h", "0", "1", "2"
    };

    private static final String[] WEATHER_VALUES = new String[] {
        "clear", "rain", "thunder", "downfall", "storm"
    };

    private static final String[] TIME_ACTION_VALUES = new String[] { "set", "add" };

    private static final String[] TIME_SET_VALUES = new String[] {
        "sunrise", "day", "noon", "sunset", "night", "midnight"
    };

    private static final String[] PROFILE_ACTION_VALUES = new String[] {
        "start", "stop", "report", "save", "status", "clear", "help"
    };

    private static final Comparator<String> CASE_INSENSITIVE_ORDER = new Comparator<String>() {
        public int compare(String a, String b) {
            if (a == b) {
                return 0;
            }
            if (a == null) {
                return 1;
            }
            if (b == null) {
                return -1;
            }
            int cmp = a.compareToIgnoreCase(b);
            if (cmp != 0) {
                return cmp;
            }
            return a.compareTo(b);
        }
    };

    private static final class Syntax {
        private final List<String> argumentIds;

        private Syntax(String[] argumentIds) {
            ArrayList<String> ids = new ArrayList<String>();
            if (argumentIds != null) {
                for (int i = 0; i < argumentIds.length; i++) {
                    String argumentId = argumentIds[i];
                    if (argumentId != null && argumentId.length() > 0) {
                        ids.add(argumentId);
                    }
                }
            }
            this.argumentIds = Collections.unmodifiableList(ids);
        }
    }

    private static final class CommandSpec {
        private final String canonicalNameLower;
        private final LinkedHashSet<String> aliasesLower = new LinkedHashSet<String>();
        private final List<Syntax> syntaxes = new ArrayList<Syntax>();

        private CommandSpec(String canonicalName) {
            this.canonicalNameLower = normalizeCommandName(canonicalName);
            this.aliasesLower.add(this.canonicalNameLower);
        }
    }

    private static final class CommandTreeEntry {
        private final String canonicalNameLower;
        private final List<String> aliasesLower;
        private final List<Syntax> syntaxes;

        private CommandTreeEntry(String canonicalNameLower, List<String> aliasesLower, List<Syntax> syntaxes) {
            this.canonicalNameLower = canonicalNameLower;
            this.aliasesLower = aliasesLower;
            this.syntaxes = syntaxes;
        }
    }

    private interface ArgumentProvider {
        List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower);

        boolean matches(CommandSender sender, String[] args, int argIndex, String token);
    }

    private static final CommandAutocompleteRegistry INSTANCE = new CommandAutocompleteRegistry();

    private final Map<String, ArgumentProvider> argumentProviders = new HashMap<String, ArgumentProvider>();
    private final Map<String, CommandSpec> commandSpecsByAlias = new HashMap<String, CommandSpec>();

    public static CommandAutocompleteRegistry getInstance() {
        return INSTANCE;
    }

    private CommandAutocompleteRegistry() {
        registerArguments();
        registerCommandSpecs();
    }

    public byte[] buildTreePayload(SimpleCommandMap commandMap, CommandSender sender) {
        if (commandMap == null || sender == null) {
            return null;
        }

        List<CommandTreeEntry> entries = collectVisibleCommands(commandMap, sender);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);

        try {
            out.writeInt(PROTOCOL_VERSION);
            out.writeInt(entries.size());

            for (int i = 0; i < entries.size(); i++) {
                CommandTreeEntry entry = entries.get(i);
                out.writeUTF(entry.canonicalNameLower);

                int aliasCount = Math.min(65535, entry.aliasesLower.size());
                out.writeShort(aliasCount);
                for (int a = 0; a < aliasCount; a++) {
                    out.writeUTF(entry.aliasesLower.get(a));
                }

                int syntaxCount = Math.min(255, entry.syntaxes.size());
                out.writeByte(syntaxCount);
                for (int s = 0; s < syntaxCount; s++) {
                    Syntax syntax = entry.syntaxes.get(s);
                    int argCount = Math.min(255, syntax.argumentIds.size());
                    out.writeByte(argCount);
                    for (int arg = 0; arg < argCount; arg++) {
                        out.writeUTF(syntax.argumentIds.get(arg));
                    }
                }
            }

            return baos.toByteArray();
        } catch (IOException ignored) {
            return null;
        } finally {
            try {
                out.close();
            } catch (IOException ignored) {}
        }
    }

    public List<String> suggest(SimpleCommandMap commandMap, CommandSender sender, String text, int cursorPos) {
        if (commandMap == null || sender == null || text == null) {
            return Collections.emptyList();
        }

        int safeCursor = clamp(cursorPos, 0, text.length());
        String uptoCursor = text.substring(0, safeCursor);
        if (uptoCursor.length() == 0) {
            return Collections.emptyList();
        }

        if (!uptoCursor.startsWith("/")) {
            return suggestPlayerNamesForChat(uptoCursor);
        }

        String commandText = uptoCursor.substring(1);
        String[] parts = commandText.split(" ", -1);
        if (parts.length == 0) {
            return Collections.emptyList();
        }

        String commandLabelLower = safeLower(parts[0]);
        if (parts.length == 1 && commandText.indexOf(' ') < 0) {
            return suggestCommandNames(commandMap, sender, commandLabelLower);
        }

        Command command = resolveAutocompleteCommand(commandMap, commandLabelLower);
        if (command == null || !canUseCommand(sender, command)) {
            return Collections.emptyList();
        }

        String[] args = new String[parts.length - 1];
        if (args.length > 0) {
            System.arraycopy(parts, 1, args, 0, args.length);
        }

        List<String> fromSpec = suggestFromSpec(commandLabelLower, sender, args);
        if (fromSpec != null) {
            return fromSpec;
        }

        String prefixLower = args.length == 0 ? "" : safeLower(args[args.length - 1]);
        return filterStrings(command.tabComplete(sender, commandLabelLower, args), prefixLower);
    }

    private List<CommandTreeEntry> collectVisibleCommands(SimpleCommandMap commandMap, CommandSender sender) {
        HashMap<String, CommandTreeEntry> byCanonical = new HashMap<String, CommandTreeEntry>();

        for (Command command : collectAutocompleteCommands(commandMap)) {
            if (command == null || !canUseCommand(sender, command)) {
                continue;
            }

            String canonical = normalizeCommandName(command.getName());
            if (canonical.length() == 0) {
                continue;
            }
            if (!isLabelRoutableToCommand(commandMap, canonical, command)) {
                continue;
            }

            ArrayList<String> aliases = new ArrayList<String>();
            List<String> commandAliases = command.getAliases();
            if (commandAliases != null) {
                for (int i = 0; i < commandAliases.size(); i++) {
                    String alias = normalizeCommandName(commandAliases.get(i));
                    if (alias.length() == 0 || canonical.equals(alias)) {
                        continue;
                    }
                    Command aliasOwner = commandMap.getCommand(alias);
                    if (aliasOwner != null && aliasOwner != command) {
                        continue;
                    }
                    if (!aliases.contains(alias)) {
                        aliases.add(alias);
                    }
                }
            }

            Collections.sort(aliases, CASE_INSENSITIVE_ORDER);
            CommandSpec spec = resolveSpec(canonical, aliases);
            List<Syntax> syntaxes = spec == null ? Collections.<Syntax>emptyList() : spec.syntaxes;
            byCanonical.put(canonical, new CommandTreeEntry(canonical, aliases, syntaxes));
        }

        ArrayList<CommandTreeEntry> out = new ArrayList<CommandTreeEntry>(byCanonical.values());
        Collections.sort(out, new Comparator<CommandTreeEntry>() {
            public int compare(CommandTreeEntry a, CommandTreeEntry b) {
                return CASE_INSENSITIVE_ORDER.compare(a.canonicalNameLower, b.canonicalNameLower);
            }
        });
        return out;
    }

    private List<String> suggestPlayerNamesForChat(String message) {
        String[] words = message.split(" ", -1);
        String prefixLower = words.length > 0 ? safeLower(words[words.length - 1]) : "";
        ArrayList<String> names = new ArrayList<String>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || player.getName() == null) {
                continue;
            }
            String name = player.getName();
            if (safeLower(name).startsWith(prefixLower)) {
                names.add(name);
            }
        }
        return sortAndDedupe(names);
    }

    private List<String> suggestCommandNames(SimpleCommandMap commandMap, CommandSender sender, String prefixLower) {
        LinkedHashSet<String> names = new LinkedHashSet<String>();
        for (Command command : collectAutocompleteCommands(commandMap)) {
            if (command == null || !canUseCommand(sender, command)) {
                continue;
            }

            String canonical = normalizeCommandName(command.getName());
            if (canonical.startsWith(prefixLower) && isLabelRoutableToCommand(commandMap, canonical, command)) {
                names.add(canonical);
            }

            List<String> aliases = command.getAliases();
            if (aliases != null) {
                for (int i = 0; i < aliases.size(); i++) {
                    String aliasLower = normalizeCommandName(aliases.get(i));
                    if (aliasLower.length() == 0 || canonical.equals(aliasLower)) {
                        continue;
                    }
                    if (aliasLower.startsWith(prefixLower) && isLabelRoutableToCommand(commandMap, aliasLower, command)) {
                        names.add(aliasLower);
                    }
                }
            }
        }
        return sortAndDedupe(names);
    }

    /**
     * Returns null when no explicit syntax exists for this command (caller should fallback).
     */
    private List<String> suggestFromSpec(String commandLabelLower, CommandSender sender, String[] args) {
        CommandSpec spec = commandSpecsByAlias.get(commandLabelLower);
        if (spec == null) {
            return null;
        }

        int argIndex = args.length - 1;
        if (argIndex < 0) {
            argIndex = 0;
        }

        String prefixLower = args.length == 0 ? "" : safeLower(args[argIndex]);

        if (argIndex == 0 && prefixLower.length() == 0) {
            for (int i = 0; i < spec.syntaxes.size(); i++) {
                Syntax syntax = spec.syntaxes.get(i);
                if (!isSyntaxViable(spec, syntax, sender, args, argIndex)) {
                    continue;
                }
                List<String> picks = suggestionsForSyntax(syntax, sender, args, argIndex, prefixLower);
                if (!picks.isEmpty()) {
                    return picks;
                }
            }
            return Collections.emptyList();
        }

        ArrayList<String> merged = new ArrayList<String>();
        for (int i = 0; i < spec.syntaxes.size(); i++) {
            Syntax syntax = spec.syntaxes.get(i);
            if (!isSyntaxViable(spec, syntax, sender, args, argIndex)) {
                continue;
            }
            merged.addAll(suggestionsForSyntax(syntax, sender, args, argIndex, prefixLower));
        }
        return sortAndDedupe(merged);
    }

    private boolean isSyntaxViable(CommandSpec spec, Syntax syntax, CommandSender sender, String[] args, int argIndex) {
        if (syntax == null || argIndex >= syntax.argumentIds.size()) {
            return false;
        }

        for (int i = 0; i < argIndex; i++) {
            if (i >= syntax.argumentIds.size()) {
                return false;
            }

            String token = args.length > i ? args[i] : "";
            if (token == null || token.length() == 0) {
                return false;
            }

            String argumentId = syntax.argumentIds.get(i);
            ArgumentProvider provider = argumentProviders.get(argumentId);
            if (provider == null || !provider.matches(sender, args, i, token)) {
                return false;
            }
        }

        return true;
    }

    private List<String> suggestionsForSyntax(Syntax syntax, CommandSender sender, String[] args, int argIndex, String prefixLower) {
        if (syntax == null || argIndex >= syntax.argumentIds.size()) {
            return Collections.emptyList();
        }
        String argumentId = syntax.argumentIds.get(argIndex);
        ArgumentProvider provider = argumentProviders.get(argumentId);
        if (provider == null) {
            return Collections.emptyList();
        }
        return provider.suggest(sender, args, argIndex, prefixLower);
    }

    private List<Command> collectAutocompleteCommands(SimpleCommandMap commandMap) {
        LinkedHashSet<Command> commands = new LinkedHashSet<Command>();
        commands.addAll(commandMap.getCommands());

        for (VanillaCommand fallback : SimpleCommandMap.fallbackCommands) {
            if (fallback == null) {
                continue;
            }
            String canonical = normalizeCommandName(fallback.getName());
            if (canonical.length() == 0) {
                continue;
            }

            Command existing = commandMap.getCommand(canonical);
            if (existing != null && existing != fallback) {
                continue;
            }
            commands.add(fallback);
        }

        return new ArrayList<Command>(commands);
    }

    private Command resolveAutocompleteCommand(SimpleCommandMap commandMap, String label) {
        String normalized = normalizeCommandName(label);
        if (normalized.length() == 0) {
            return null;
        }

        Command command = commandMap.getCommand(normalized);
        if (command != null) {
            return command;
        }

        for (VanillaCommand fallback : SimpleCommandMap.fallbackCommands) {
            if (isCommandLabel(fallback, normalized)) {
                return fallback;
            }
        }

        return null;
    }

    private boolean isLabelRoutableToCommand(SimpleCommandMap commandMap, String label, Command command) {
        String normalized = normalizeCommandName(label);
        if (normalized.length() == 0 || command == null) {
            return false;
        }

        Command existing = commandMap.getCommand(normalized);
        if (existing != null) {
            return existing == command;
        }

        return isCommandLabel(command, normalized);
    }

    private boolean isCommandLabel(Command command, String label) {
        if (command == null) {
            return false;
        }

        String normalized = normalizeCommandName(label);
        if (normalized.length() == 0) {
            return false;
        }

        if (normalized.equals(normalizeCommandName(command.getName()))) {
            return true;
        }

        List<String> aliases = command.getAliases();
        if (aliases != null) {
            for (int i = 0; i < aliases.size(); i++) {
                if (normalized.equals(normalizeCommandName(aliases.get(i)))) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean canUseCommand(CommandSender sender, Command command) {
        if (sender == null || command == null) {
            return false;
        }
        String permission = command.getPermission();
        return permission == null || permission.length() == 0 || sender.hasPermission(permission) || sender.isOp();
    }

    private CommandSpec resolveSpec(String canonical, List<String> aliases) {
        CommandSpec spec = commandSpecsByAlias.get(canonical);
        if (spec != null) {
            return spec;
        }
        if (aliases != null) {
            for (int i = 0; i < aliases.size(); i++) {
                spec = commandSpecsByAlias.get(aliases.get(i));
                if (spec != null) {
                    return spec;
                }
            }
        }
        return null;
    }

    private void registerArguments() {
        registerLiteralArgument(ARG_GAMEMODE, GAMEMODE_VALUES);
        registerLiteralArgument(ARG_WEATHER_TYPE, WEATHER_VALUES);
        registerLiteralArgument(ARG_TIME_ACTION, TIME_ACTION_VALUES);
        registerLiteralArgument(ARG_PROFILE_ACTION, PROFILE_ACTION_VALUES);

        registerArgument(ARG_PLAYER, new ArgumentProvider() {
            public List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower) {
                ArrayList<String> names = new ArrayList<String>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player == null || player.getName() == null) {
                        continue;
                    }
                    String name = player.getName();
                    if (safeLower(name).startsWith(prefixLower)) {
                        names.add(name);
                    }
                }
                return sortAndDedupe(names);
            }

            public boolean matches(CommandSender sender, String[] args, int argIndex, String token) {
                if (token == null || token.length() == 0) {
                    return false;
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player != null && player.getName() != null && player.getName().equalsIgnoreCase(token)) {
                        return true;
                    }
                }
                return false;
            }
        });

        registerArgument(ARG_ITEM_KEY, new ArgumentProvider() {
            public List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower) {
                return filterResourceKeys(ItemRegistry.displayKeys(), prefixLower);
            }

            public boolean matches(CommandSender sender, String[] args, int argIndex, String token) {
                return ItemRegistry.normalizeInputIdentifier(token) != null;
            }
        });

        registerArgument(ARG_BLOCK_KEY, new ArgumentProvider() {
            public List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower) {
                return filterResourceKeys(BlockRegistry.displayKeys(), prefixLower);
            }

            public boolean matches(CommandSender sender, String[] args, int argIndex, String token) {
                return BlockRegistry.normalizeInputIdentifier(token) != null;
            }
        });

        registerArgument(ARG_ENTITY_KEY, new ArgumentProvider() {
            public List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower) {
                ArrayList<ResourceLocation> summonable = new ArrayList<ResourceLocation>();
                for (ResourceLocation key : EntityTypeRegistry.primaryKeys()) {
                    if (isSummonableEntityKey(key)) {
                        summonable.add(key);
                    }
                }
                return filterResourceKeys(summonable, prefixLower);
            }

            public boolean matches(CommandSender sender, String[] args, int argIndex, String token) {
                String normalized = EntityTypeRegistry.normalizeInputIdentifier(token);
                if (normalized == null) {
                    return false;
                }
                return isSummonableEntityKey(new ResourceLocation(normalized));
            }
        });

        registerArgument(ARG_STRUCTURE_KEY, new ArgumentProvider() {
            public List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower) {
                try {
                    StructureTypes.initialize();
                } catch (Throwable ignored) {}
                return filterResourceKeys(Registries.STRUCTURE_TYPE.keys(), prefixLower);
            }

            public boolean matches(CommandSender sender, String[] args, int argIndex, String token) {
                try {
                    StructureTypes.initialize();
                } catch (Throwable ignored) {}
                return StructureTypes.normalizeInputIdentifier(token) != null;
            }
        });

        registerArgument(ARG_TIME_VALUE, new ArgumentProvider() {
            public List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower) {
                String action = safeLower(argAt(args, 0));
                if ("set".equals(action)) {
                    ArrayList<String> values = new ArrayList<String>(Arrays.asList(TIME_SET_VALUES));
                    values.addAll(Arrays.asList("0", "1000", "6000", "12000", "13000", "18000"));
                    return filterStrings(values, prefixLower);
                }
                return filterStrings(Arrays.asList("0", "1000", "6000", "12000", "13000", "18000"), prefixLower);
            }

            public boolean matches(CommandSender sender, String[] args, int argIndex, String token) {
                String action = safeLower(argAt(args, 0));
                if ("set".equals(action) && containsIgnoreCase(TIME_SET_VALUES, token)) {
                    return true;
                }
                return isIntegerToken(token);
            }
        });

        registerArgument(ARG_GAMERULE_NAME, new ArgumentProvider() {
            public List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower) {
                ArrayList<String> values = new ArrayList<String>();
                values.addAll(Arrays.asList(BOOLEAN_GAMERULES));
                values.addAll(Arrays.asList(INTEGER_GAMERULES));
                return filterStrings(values, prefixLower);
            }

            public boolean matches(CommandSender sender, String[] args, int argIndex, String token) {
                return isBooleanGamerule(token) || isIntegerGamerule(token);
            }
        });

        registerArgument(ARG_GAMERULE_VALUE, new ArgumentProvider() {
            public List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower) {
                String rule = safeLower(argAt(args, 0));
                if (isBooleanGamerule(rule)) {
                    return filterStrings(Arrays.asList("true", "false"), prefixLower);
                }
                return Collections.emptyList();
            }

            public boolean matches(CommandSender sender, String[] args, int argIndex, String token) {
                String rule = safeLower(argAt(args, 0));
                if (isBooleanGamerule(rule)) {
                    return "true".equalsIgnoreCase(token) || "false".equalsIgnoreCase(token);
                }
                if (isIntegerGamerule(rule)) {
                    return isIntegerToken(token);
                }
                return false;
            }
        });

        registerArgument(ARG_INTEGER, new ArgumentProvider() {
            public List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower) {
                return filterStrings(Arrays.asList("1", "8", "16", "32", "64"), prefixLower);
            }

            public boolean matches(CommandSender sender, String[] args, int argIndex, String token) {
                return isIntegerToken(token);
            }
        });

        registerArgument(ARG_COORD, new ArgumentProvider() {
            public List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower) {
                if (prefixLower.startsWith("~")) {
                    return filterStrings(Arrays.asList("~", "~1", "~-1"), prefixLower);
                }
                return filterStrings(Arrays.asList("~", "0", "1", "-1"), prefixLower);
            }

            public boolean matches(CommandSender sender, String[] args, int argIndex, String token) {
                return isCoordinateToken(token);
            }
        });

        registerArgument(ARG_TEXT, new ArgumentProvider() {
            public List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower) {
                return Collections.emptyList();
            }

            public boolean matches(CommandSender sender, String[] args, int argIndex, String token) {
                return token != null && token.length() > 0;
            }
        });
    }

    private void registerCommandSpecs() {
        registerSpec("admin", new String[] { "openinventory", "openinv" }, new String[][] {
            { ARG_PLAYER }
        });

        registerSpec("give", new String[0], new String[][] {
            { ARG_PLAYER, ARG_ITEM_KEY },
            { ARG_PLAYER, ARG_ITEM_KEY, ARG_INTEGER }
        });

        registerSpec("gamerule", new String[0], new String[][] {
            { ARG_GAMERULE_NAME },
            { ARG_GAMERULE_NAME, ARG_GAMERULE_VALUE }
        });

        registerSpec("weather", new String[] { "toggledownfall" }, new String[][] {
            { ARG_WEATHER_TYPE },
            { ARG_WEATHER_TYPE, ARG_INTEGER }
        });

        registerSpec("time", new String[0], new String[][] {
            { ARG_TIME_ACTION, ARG_TIME_VALUE }
        });

        registerSpec("gamemode", new String[0], new String[][] {
            { ARG_PLAYER, ARG_GAMEMODE },
            { ARG_GAMEMODE },
            { ARG_GAMEMODE, ARG_PLAYER }
        });

        registerSpec("summon", new String[0], new String[][] {
            { ARG_ENTITY_KEY },
            { ARG_ENTITY_KEY, ARG_COORD, ARG_COORD, ARG_COORD },
            { ARG_ENTITY_KEY, ARG_COORD, ARG_COORD, ARG_COORD, ARG_BLOCK_KEY }
        });

        registerSpec("tp", new String[] { "teleport" }, new String[][] {
            { ARG_PLAYER },
            { ARG_PLAYER, ARG_PLAYER },
            { ARG_COORD, ARG_COORD, ARG_COORD },
            { ARG_PLAYER, ARG_COORD, ARG_COORD, ARG_COORD }
        });

        registerSpec("tell", new String[0], new String[][] {
            { ARG_PLAYER, ARG_TEXT }
        });

        registerSpec("locate", new String[0], new String[][] {
            { ARG_STRUCTURE_KEY }
        });

        registerSpec("profile", new String[] { "profiler" }, new String[][] {
            { ARG_PROFILE_ACTION }
        });

        registerSpec("setworldspawn", new String[0], new String[][] {
            {},
            { ARG_COORD, ARG_COORD, ARG_COORD }
        });
    }

    private void registerSpec(String canonical, String[] aliases, String[][] syntaxes) {
        CommandSpec spec = new CommandSpec(canonical);
        if (spec.canonicalNameLower.length() == 0) {
            return;
        }

        if (aliases != null) {
            for (int i = 0; i < aliases.length; i++) {
                String alias = normalizeCommandName(aliases[i]);
                if (alias.length() > 0) {
                    spec.aliasesLower.add(alias);
                }
            }
        }

        if (syntaxes != null) {
            for (int i = 0; i < syntaxes.length; i++) {
                spec.syntaxes.add(new Syntax(syntaxes[i]));
            }
        }

        for (String alias : spec.aliasesLower) {
            commandSpecsByAlias.put(alias, spec);
        }
    }

    private void registerArgument(String argumentId, ArgumentProvider provider) {
        if (argumentId == null || provider == null) {
            return;
        }
        argumentProviders.put(argumentId, provider);
    }

    private void registerLiteralArgument(final String argumentId, final String[] values) {
        registerArgument(argumentId, new ArgumentProvider() {
            public List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower) {
                return filterStrings(Arrays.asList(values), prefixLower);
            }

            public boolean matches(CommandSender sender, String[] args, int argIndex, String token) {
                return containsIgnoreCase(values, token);
            }
        });
    }

    private static boolean isSummonableEntityKey(ResourceLocation key) {
        if (key == null) {
            return false;
        }
        Class<?> entityClass = EntityTypeRegistry.get(key);
        if (entityClass == null) {
            return false;
        }
        return entityClass != EntityItem.class && entityClass != EntityPainting.class;
    }

    private static List<String> filterStrings(Collection<String> values, String prefixLower) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        String prefix = safeLower(prefixLower);
        LinkedHashSet<String> out = new LinkedHashSet<String>();
        for (String value : values) {
            if (value == null || value.length() == 0) {
                continue;
            }
            if (safeLower(value).startsWith(prefix)) {
                out.add(value);
            }
        }
        return sortAndDedupe(out);
    }

    private static List<String> filterResourceKeys(Collection<ResourceLocation> keys, String prefixLower) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }

        String prefix = safeLower(prefixLower);
        boolean namespacedInput = prefix.indexOf(':') >= 0;
        LinkedHashSet<String> out = new LinkedHashSet<String>();

        for (ResourceLocation key : keys) {
            if (key == null) {
                continue;
            }

            String full = key.toString();
            String path = key.getPath();
            if (namespacedInput) {
                if (safeLower(full).startsWith(prefix)) {
                    out.add(full);
                }
            } else {
                if (safeLower(path).startsWith(prefix)) {
                    out.add(path);
                }
            }
        }

        return sortAndDedupe(out);
    }

    private static List<String> sortAndDedupe(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<String> out = new ArrayList<String>();
        LinkedHashSet<String> seen = new LinkedHashSet<String>();
        for (String value : values) {
            if (value == null || value.length() == 0) {
                continue;
            }
            if (seen.add(value)) {
                out.add(value);
            }
        }
        Collections.sort(out, CASE_INSENSITIVE_ORDER);
        return out;
    }

    private static String normalizeCommandName(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim().toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        int space = trimmed.indexOf(' ');
        if (space >= 0) {
            trimmed = trimmed.substring(0, space);
        }
        return trimmed;
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String argAt(String[] args, int index) {
        if (args == null || index < 0 || index >= args.length) {
            return "";
        }
        String value = args[index];
        return value == null ? "" : value;
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static boolean containsIgnoreCase(String[] values, String token) {
        if (values == null || token == null) {
            return false;
        }
        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            if (value != null && value.equalsIgnoreCase(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIntegerToken(String token) {
        if (token == null || token.length() == 0) {
            return false;
        }
        try {
            Integer.parseInt(token);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean isCoordinateToken(String token) {
        if (token == null || token.length() == 0) {
            return false;
        }
        if ("~".equals(token)) {
            return true;
        }
        if (token.startsWith("~")) {
            String remainder = token.substring(1);
            if (remainder.length() == 0) {
                return true;
            }
            try {
                Double.parseDouble(remainder);
                return true;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        try {
            Double.parseDouble(token);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean isBooleanGamerule(String value) {
        return containsIgnoreCase(BOOLEAN_GAMERULES, value);
    }

    private static boolean isIntegerGamerule(String value) {
        return containsIgnoreCase(INTEGER_GAMERULES, value);
    }
}
