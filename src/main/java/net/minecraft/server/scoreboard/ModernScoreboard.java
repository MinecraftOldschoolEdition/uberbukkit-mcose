package net.minecraft.server.scoreboard;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Server-authoritative scoreboard state, adapted from the 26.3 scoreboard
 * model.  It deliberately contains no Bukkit or packet concerns so commands,
 * persistence, criteria hooks, and synchronization all mutate one contract.
 */
public final class ModernScoreboard {
    public static final int DATA_VERSION = 1;
    public static final int DISPLAY_SLOT_COUNT = 19;
    public static final int MAX_OBJECTIVES = 1024;
    public static final int MAX_SCORES = 65536;
    public static final int MAX_TEAMS = 1024;
    public static final int MAX_TEAM_MEMBERS = 65536;
    public static final int MAX_OBJECTIVE_NAME = 16;
    public static final int MAX_TEAM_NAME = 16;
    public static final int MAX_HOLDER_NAME = 40;
    public static final int MAX_TEXT = 1024;

    public static final String[] DISPLAY_SLOT_NAMES = new String[] {
        "list", "sidebar", "below_name",
        "sidebar.team.black", "sidebar.team.dark_blue", "sidebar.team.dark_green",
        "sidebar.team.dark_aqua", "sidebar.team.dark_red", "sidebar.team.dark_purple",
        "sidebar.team.gold", "sidebar.team.gray", "sidebar.team.dark_gray",
        "sidebar.team.blue", "sidebar.team.green", "sidebar.team.aqua",
        "sidebar.team.red", "sidebar.team.light_purple", "sidebar.team.yellow",
        "sidebar.team.white"
    };

    public enum RenderType {
        INTEGER("integer"), HEARTS("hearts");

        public final String id;

        RenderType(String id) {
            this.id = id;
        }

        public static RenderType byName(String value) {
            return "hearts".equalsIgnoreCase(value) ? HEARTS : INTEGER;
        }
    }

    public enum FormatType {
        DEFAULT, BLANK, FIXED, STYLED
    }

    public static final class NumberFormat {
        public final FormatType type;
        public final String value;

        private NumberFormat(FormatType type, String value) {
            this.type = type == null ? FormatType.DEFAULT : type;
            this.value = cleanText(value);
        }

        public static NumberFormat defaultFormat() {
            return new NumberFormat(FormatType.DEFAULT, "");
        }

        public static NumberFormat blank() {
            return new NumberFormat(FormatType.BLANK, "");
        }

        public static NumberFormat fixed(String value) {
            return new NumberFormat(FormatType.FIXED, value);
        }

        public static NumberFormat styled(String legacyStylePrefix) {
            return new NumberFormat(FormatType.STYLED, legacyStylePrefix);
        }

        public String format(int score, String defaultPrefix) {
            if (this.type == FormatType.BLANK) {
                return "";
            }
            if (this.type == FormatType.FIXED) {
                return this.value;
            }
            if (this.type == FormatType.STYLED) {
                return this.value + score;
            }
            return cleanText(defaultPrefix) + score;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof NumberFormat)) return false;
            NumberFormat that = (NumberFormat) other;
            return this.type == that.type && this.value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return this.type.hashCode() * 31 + this.value.hashCode();
        }
    }

    public static final class Objective {
        public final String name;
        public final String criteria;
        public String displayName;
        public RenderType renderType;
        public boolean displayAutoUpdate;
        public NumberFormat numberFormat;

        Objective(String name, String criteria, String displayName, RenderType renderType) {
            this.name = requireName(name, MAX_OBJECTIVE_NAME, "objective");
            this.criteria = requireText(criteria, 128, "criteria");
            this.displayName = cleanText(displayName.length() == 0 ? name : displayName);
            this.renderType = renderType == null ? defaultRenderType(criteria) : renderType;
            this.numberFormat = NumberFormat.defaultFormat();
        }

        public boolean isReadOnly() {
            return isReadOnlyCriteria(this.criteria);
        }
    }

    public static final class Score {
        public int value;
        public boolean locked = true;
        public String displayName;
        public NumberFormat numberFormat = NumberFormat.defaultFormat();

        Score(int value) {
            this.value = value;
        }
    }

    public static final class ScoreEntry {
        public final String owner;
        public final Objective objective;
        public final Score score;

        ScoreEntry(String owner, Objective objective, Score score) {
            this.owner = owner;
            this.objective = objective;
            this.score = score;
        }

        public boolean isHidden() {
            return this.owner.startsWith("#");
        }

        public String displayOwner() {
            return this.score.displayName == null ? this.owner : this.score.displayName;
        }

        public String formattedValue(String defaultPrefix) {
            NumberFormat format = this.score.numberFormat.type == FormatType.DEFAULT
                ? this.objective.numberFormat : this.score.numberFormat;
            return format.format(this.score.value, defaultPrefix);
        }
    }

    public static final class Team {
        public final String name;
        public String displayName;
        public int color = -1;
        public boolean friendlyFire = true;
        public boolean seeFriendlyInvisibles = true;
        public String prefix = "";
        public String suffix = "";
        public String nameTagVisibility = "always";
        public String deathMessageVisibility = "always";
        public String collisionRule = "always";
        public final Set<String> players = new LinkedHashSet<String>();

        Team(String name, String displayName) {
            this.name = requireName(name, MAX_TEAM_NAME, "team");
            this.displayName = cleanText(displayName.length() == 0 ? name : displayName);
        }
    }

    private final Map<String, Objective> objectives = new LinkedHashMap<String, Objective>();
    private final Map<String, Map<String, Score>> scoresByOwner = new LinkedHashMap<String, Map<String, Score>>();
    private final String[] displayObjectives = new String[DISPLAY_SLOT_COUNT];
    private final Map<String, Team> teams = new LinkedHashMap<String, Team>();
    private final Map<String, String> teamByPlayer = new LinkedHashMap<String, String>();
    private Runnable changeListener;
    private int revision;

    public synchronized void setChangeListener(Runnable listener) {
        this.changeListener = listener;
    }

    public synchronized int getRevision() {
        return this.revision;
    }

    public synchronized Collection<Objective> getObjectives() {
        return new ArrayList<Objective>(this.objectives.values());
    }

    public synchronized Objective getObjective(String name) {
        return name == null ? null : this.objectives.get(name);
    }

    public synchronized Objective addObjective(String name, String criteria, String displayName) {
        if (this.objectives.containsKey(name)) {
            throw new IllegalArgumentException("Objective already exists: " + name);
        }
        if (this.objectives.size() >= MAX_OBJECTIVES) {
            throw new IllegalStateException("Too many objectives");
        }
        Objective objective = new Objective(name, criteria, displayName == null ? name : displayName, null);
        this.objectives.put(objective.name, objective);
        changed();
        return objective;
    }

    public synchronized boolean removeObjective(String name) {
        Objective removed = this.objectives.remove(name);
        if (removed == null) return false;
        for (int i = 0; i < this.displayObjectives.length; ++i) {
            if (name.equals(this.displayObjectives[i])) this.displayObjectives[i] = null;
        }
        for (Map<String, Score> scores : this.scoresByOwner.values()) scores.remove(name);
        removeEmptyScoreHolders();
        changed();
        return true;
    }

    public synchronized void updateObjective(Objective objective, String displayName,
                                             RenderType renderType, Boolean autoUpdate,
                                             NumberFormat numberFormat) {
        requireOwned(objective);
        if (displayName != null) objective.displayName = cleanText(displayName);
        if (renderType != null) objective.renderType = renderType;
        if (autoUpdate != null) objective.displayAutoUpdate = autoUpdate.booleanValue();
        if (numberFormat != null) objective.numberFormat = numberFormat;
        changed();
    }

    public synchronized void setObjectiveNumberFormat(Objective objective, NumberFormat numberFormat) {
        requireOwned(objective);
        objective.numberFormat = numberFormat == null ? NumberFormat.defaultFormat() : numberFormat;
        changed();
    }

    public synchronized void setDisplayObjective(int slot, String objectiveName) {
        if (slot < 0 || slot >= DISPLAY_SLOT_COUNT) throw new IllegalArgumentException("Unknown display slot");
        if (objectiveName != null && !this.objectives.containsKey(objectiveName)) {
            throw new IllegalArgumentException("Unknown objective: " + objectiveName);
        }
        this.displayObjectives[slot] = objectiveName;
        changed();
    }

    public synchronized Objective getDisplayObjective(int slot) {
        if (slot < 0 || slot >= DISPLAY_SLOT_COUNT) return null;
        return this.objectives.get(this.displayObjectives[slot]);
    }

    public static int displaySlotByName(String name) {
        for (int i = 0; i < DISPLAY_SLOT_NAMES.length; ++i) {
            if (DISPLAY_SLOT_NAMES[i].equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    public synchronized Score getScore(String owner, String objectiveName) {
        Map<String, Score> scores = this.scoresByOwner.get(owner);
        return scores == null ? null : scores.get(objectiveName);
    }

    public synchronized Score getOrCreateScore(String owner, Objective objective, boolean forceWritable) {
        requireOwned(objective);
        owner = requireName(owner, MAX_HOLDER_NAME, "score holder");
        if (objective.isReadOnly() && !forceWritable) {
            throw new IllegalStateException("Cannot modify read-only objective " + objective.name);
        }
        Map<String, Score> scores = this.scoresByOwner.get(owner);
        if (scores == null) {
            if (scoreCount() >= MAX_SCORES) throw new IllegalStateException("Too many scores");
            scores = new LinkedHashMap<String, Score>();
            this.scoresByOwner.put(owner, scores);
        }
        Score score = scores.get(objective.name);
        if (score == null) {
            score = new Score(0);
            scores.put(objective.name, score);
            changed();
        }
        return score;
    }

    public synchronized void setScore(String owner, Objective objective, int value, boolean forceWritable) {
        Score score = getOrCreateScore(owner, objective, forceWritable);
        if (score.value != value) {
            score.value = value;
            if (objective.displayAutoUpdate) score.displayName = owner;
            changed();
        }
    }

    public synchronized void setScoreDisplay(String owner, Objective objective, String displayName) {
        Score score = getOrCreateScore(owner, objective, true);
        score.displayName = displayName == null ? null : cleanText(displayName);
        changed();
    }

    public synchronized void setScoreNumberFormat(String owner, Objective objective, NumberFormat format) {
        Score score = getOrCreateScore(owner, objective, true);
        score.numberFormat = format == null ? NumberFormat.defaultFormat() : format;
        changed();
    }

    public synchronized void setScoreLocked(String owner, Objective objective, boolean locked) {
        Score score = getOrCreateScore(owner, objective, true);
        if (score.locked != locked) {
            score.locked = locked;
            changed();
        }
    }

    public synchronized boolean resetScore(String owner, String objectiveName) {
        Map<String, Score> scores = this.scoresByOwner.get(owner);
        if (scores == null) return false;
        boolean changed = objectiveName == null ? !scores.isEmpty() : scores.remove(objectiveName) != null;
        if (objectiveName == null) scores.clear();
        if (scores.isEmpty()) this.scoresByOwner.remove(owner);
        if (changed) changed();
        return changed;
    }

    public synchronized Collection<String> getTrackedHolders() {
        return new ArrayList<String>(this.scoresByOwner.keySet());
    }

    public synchronized List<ScoreEntry> getScores(String owner) {
        List<ScoreEntry> result = new ArrayList<ScoreEntry>();
        Map<String, Score> scores = this.scoresByOwner.get(owner);
        if (scores == null) return result;
        for (Map.Entry<String, Score> entry : scores.entrySet()) {
            Objective objective = this.objectives.get(entry.getKey());
            if (objective != null) result.add(new ScoreEntry(owner, objective, entry.getValue()));
        }
        return result;
    }

    public synchronized List<ScoreEntry> getScores(Objective objective) {
        requireOwned(objective);
        List<ScoreEntry> result = new ArrayList<ScoreEntry>();
        for (Map.Entry<String, Map<String, Score>> owner : this.scoresByOwner.entrySet()) {
            Score score = owner.getValue().get(objective.name);
            if (score != null) result.add(new ScoreEntry(owner.getKey(), objective, score));
        }
        Collections.sort(result, new Comparator<ScoreEntry>() {
            public int compare(ScoreEntry left, ScoreEntry right) {
                int scoreOrder = Integer.compare(right.score.value, left.score.value);
                return scoreOrder != 0 ? scoreOrder : String.CASE_INSENSITIVE_ORDER.compare(left.owner, right.owner);
            }
        });
        return result;
    }

    public synchronized Collection<Team> getTeams() {
        return new ArrayList<Team>(this.teams.values());
    }

    public synchronized Team getTeam(String name) {
        return name == null ? null : this.teams.get(name);
    }

    public synchronized Team addTeam(String name, String displayName) {
        if (this.teams.containsKey(name)) throw new IllegalArgumentException("Team already exists: " + name);
        if (this.teams.size() >= MAX_TEAMS) throw new IllegalStateException("Too many teams");
        Team team = new Team(name, displayName == null ? name : displayName);
        this.teams.put(team.name, team);
        changed();
        return team;
    }

    public synchronized boolean removeTeam(String name) {
        Team removed = this.teams.remove(name);
        if (removed == null) return false;
        for (String player : removed.players) this.teamByPlayer.remove(player);
        changed();
        return true;
    }

    public synchronized void updateTeam(Team team) {
        requireOwned(team);
        team.displayName = cleanText(team.displayName);
        team.prefix = cleanText(team.prefix);
        team.suffix = cleanText(team.suffix);
        team.nameTagVisibility = normalizeVisibility(team.nameTagVisibility);
        team.deathMessageVisibility = normalizeVisibility(team.deathMessageVisibility);
        team.collisionRule = normalizeCollision(team.collisionRule);
        if (team.color < -1 || team.color > 15) team.color = -1;
        changed();
    }

    public synchronized boolean joinTeam(String player, Team team) {
        requireOwned(team);
        player = requireName(player, MAX_HOLDER_NAME, "team member");
        String oldName = this.teamByPlayer.get(player);
        if (team.name.equals(oldName)) return false;
        if (oldName != null) {
            Team old = this.teams.get(oldName);
            if (old != null) old.players.remove(player);
        }
        if (totalTeamMembers() >= MAX_TEAM_MEMBERS && !this.teamByPlayer.containsKey(player)) {
            throw new IllegalStateException("Too many team members");
        }
        this.teamByPlayer.put(player, team.name);
        team.players.add(player);
        changed();
        return true;
    }

    public synchronized boolean leaveTeam(String player) {
        String oldName = this.teamByPlayer.remove(player);
        if (oldName == null) return false;
        Team old = this.teams.get(oldName);
        if (old != null) old.players.remove(player);
        changed();
        return true;
    }

    public synchronized int emptyTeam(Team team) {
        requireOwned(team);
        int count = team.players.size();
        for (String player : new ArrayList<String>(team.players)) this.teamByPlayer.remove(player);
        team.players.clear();
        if (count > 0) changed();
        return count;
    }

    public synchronized Team getPlayersTeam(String player) {
        return this.teams.get(this.teamByPlayer.get(player));
    }

    public synchronized boolean allowsFriendlyFire(String attacker, String victim) {
        Team left = getPlayersTeam(attacker);
        Team right = getPlayersTeam(victim);
        return left == null || left != right || left.friendlyFire;
    }

    public synchronized boolean allowsCollision(String first, String second) {
        Team left = getPlayersTeam(first);
        Team right = getPlayersTeam(second);
        boolean same = left != null && left == right;
        return collisionAllows(left, same) && collisionAllows(right, same);
    }

    private static boolean collisionAllows(Team team, boolean sameTeam) {
        if (team == null || "always".equals(team.collisionRule)) return true;
        if ("never".equals(team.collisionRule)) return false;
        if ("pushOwnTeam".equals(team.collisionRule)) return sameTeam;
        if ("pushOtherTeams".equals(team.collisionRule)) return !sameTeam;
        return true;
    }

    public synchronized void incrementCriteria(String owner, String criteria, int amount) {
        if (amount == 0) return;
        for (Objective objective : this.objectives.values()) {
            if (!objective.criteria.equals(criteria)) continue;
            Score score = getOrCreateScore(owner, objective, true);
            long next = (long) score.value + amount;
            score.value = next > Integer.MAX_VALUE ? Integer.MAX_VALUE
                : next < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) next;
            if (objective.displayAutoUpdate) score.displayName = owner;
            changed();
        }
    }

    public synchronized void updateReadOnlyCriteria(String owner, String criteria, int value) {
        for (Objective objective : this.objectives.values()) {
            if (objective.criteria.equals(criteria)) setScore(owner, objective, value, true);
        }
    }

    public synchronized void write(DataOutput out) throws IOException {
        out.writeInt(DATA_VERSION);
        out.writeInt(this.revision);
        out.writeInt(this.objectives.size());
        for (Objective objective : this.objectives.values()) writeObjective(out, objective);
        for (int i = 0; i < DISPLAY_SLOT_COUNT; ++i) writeNullable(out, this.displayObjectives[i]);
        out.writeInt(scoreCount());
        for (Map.Entry<String, Map<String, Score>> owner : this.scoresByOwner.entrySet()) {
            for (Map.Entry<String, Score> score : owner.getValue().entrySet()) {
                out.writeUTF(owner.getKey());
                out.writeUTF(score.getKey());
                writeScore(out, score.getValue());
            }
        }
        out.writeInt(this.teams.size());
        for (Team team : this.teams.values()) writeTeam(out, team);
    }

    public static ModernScoreboard read(DataInput in) throws IOException {
        int version = in.readInt();
        if (version != DATA_VERSION) throw new IOException("Unsupported scoreboard data version " + version);
        ModernScoreboard board = new ModernScoreboard();
        board.revision = in.readInt();
        int objectiveCount = checkedCount(in.readInt(), MAX_OBJECTIVES, "objectives");
        for (int i = 0; i < objectiveCount; ++i) {
            Objective objective = readObjective(in);
            board.objectives.put(objective.name, objective);
        }
        for (int i = 0; i < DISPLAY_SLOT_COUNT; ++i) {
            String name = readNullable(in, MAX_OBJECTIVE_NAME);
            board.displayObjectives[i] = board.objectives.containsKey(name) ? name : null;
        }
        int scoreCount = checkedCount(in.readInt(), MAX_SCORES, "scores");
        for (int i = 0; i < scoreCount; ++i) {
            String owner = readText(in, MAX_HOLDER_NAME, "score holder");
            String objectiveName = readText(in, MAX_OBJECTIVE_NAME, "score objective");
            if (!board.objectives.containsKey(objectiveName)) throw new IOException("Unknown score objective " + objectiveName);
            Map<String, Score> scores = board.scoresByOwner.get(owner);
            if (scores == null) {
                scores = new LinkedHashMap<String, Score>();
                board.scoresByOwner.put(owner, scores);
            }
            scores.put(objectiveName, readScore(in));
        }
        int teamCount = checkedCount(in.readInt(), MAX_TEAMS, "teams");
        int memberCount = 0;
        for (int i = 0; i < teamCount; ++i) {
            Team team = readTeam(in);
            if (board.teams.put(team.name, team) != null) throw new IOException("Duplicate team " + team.name);
            for (String player : team.players) {
                if (board.teamByPlayer.put(player, team.name) != null) throw new IOException("Player belongs to multiple teams: " + player);
                if (++memberCount > MAX_TEAM_MEMBERS) throw new IOException("Too many team members");
            }
        }
        return board;
    }

    public synchronized void replaceWith(ModernScoreboard source) {
        this.objectives.clear();
        this.scoresByOwner.clear();
        this.teams.clear();
        this.teamByPlayer.clear();
        for (int i = 0; i < DISPLAY_SLOT_COUNT; ++i) this.displayObjectives[i] = null;
        this.objectives.putAll(source.objectives);
        this.scoresByOwner.putAll(source.scoresByOwner);
        this.teams.putAll(source.teams);
        this.teamByPlayer.putAll(source.teamByPlayer);
        System.arraycopy(source.displayObjectives, 0, this.displayObjectives, 0, DISPLAY_SLOT_COUNT);
        this.revision = source.revision;
    }

    private void changed() {
        ++this.revision;
        if (this.changeListener != null) this.changeListener.run();
    }

    private int scoreCount() {
        int count = 0;
        for (Map<String, Score> scores : this.scoresByOwner.values()) count += scores.size();
        return count;
    }

    private int totalTeamMembers() {
        return this.teamByPlayer.size();
    }

    private void removeEmptyScoreHolders() {
        List<String> empty = new ArrayList<String>();
        for (Map.Entry<String, Map<String, Score>> entry : this.scoresByOwner.entrySet()) {
            if (entry.getValue().isEmpty()) empty.add(entry.getKey());
        }
        for (String owner : empty) this.scoresByOwner.remove(owner);
    }

    private void requireOwned(Objective objective) {
        if (objective == null || this.objectives.get(objective.name) != objective) {
            throw new IllegalArgumentException("Objective does not belong to this scoreboard");
        }
    }

    private void requireOwned(Team team) {
        if (team == null || this.teams.get(team.name) != team) {
            throw new IllegalArgumentException("Team does not belong to this scoreboard");
        }
    }

    public static boolean isReadOnlyCriteria(String criteria) {
        if (criteria == null) return false;
        return criteria.equals("health") || criteria.equals("food") || criteria.equals("air")
            || criteria.equals("armor") || criteria.equals("xp") || criteria.equals("level")
            || criteria.indexOf(':') >= 0;
    }

    private static RenderType defaultRenderType(String criteria) {
        return "health".equals(criteria) ? RenderType.HEARTS : RenderType.INTEGER;
    }

    public static String normalizeVisibility(String value) {
        if (value == null) return "always";
        if (value.equalsIgnoreCase("never")) return "never";
        if (value.equalsIgnoreCase("hideForOtherTeams")) return "hideForOtherTeams";
        if (value.equalsIgnoreCase("hideForOwnTeam")) return "hideForOwnTeam";
        if (value.equalsIgnoreCase("always")) return "always";
        throw new IllegalArgumentException("Unknown visibility: " + value);
    }

    public static String normalizeCollision(String value) {
        if (value == null) return "always";
        if (value.equalsIgnoreCase("never")) return "never";
        if (value.equalsIgnoreCase("pushOwnTeam")) return "pushOwnTeam";
        if (value.equalsIgnoreCase("pushOtherTeams")) return "pushOtherTeams";
        if (value.equalsIgnoreCase("always")) return "always";
        throw new IllegalArgumentException("Unknown collision rule: " + value);
    }

    private static String cleanText(String value) {
        if (value == null) return "";
        if (value.length() > MAX_TEXT) return value.substring(0, MAX_TEXT);
        return value;
    }

    private static String requireName(String value, int max, String label) {
        if (value == null || value.length() == 0 || value.length() > max) {
            throw new IllegalArgumentException(label + " must be 1.." + max + " characters");
        }
        for (int i = 0; i < value.length(); ++i) {
            char ch = value.charAt(i);
            if (Character.isISOControl(ch) || Character.isWhitespace(ch)) {
                throw new IllegalArgumentException(label + " contains invalid characters");
            }
        }
        return value;
    }

    private static String requireText(String value, int max, String label) {
        if (value == null || value.length() == 0 || value.length() > max) {
            throw new IllegalArgumentException(label + " must be 1.." + max + " characters");
        }
        return value;
    }

    private static int checkedCount(int value, int max, String label) throws IOException {
        if (value < 0 || value > max) throw new IOException("Invalid " + label + " count " + value);
        return value;
    }

    private static void writeObjective(DataOutput out, Objective objective) throws IOException {
        out.writeUTF(objective.name);
        out.writeUTF(objective.criteria);
        out.writeUTF(objective.displayName);
        out.writeByte(objective.renderType.ordinal());
        out.writeBoolean(objective.displayAutoUpdate);
        writeFormat(out, objective.numberFormat);
    }

    private static Objective readObjective(DataInput in) throws IOException {
        String name = readText(in, MAX_OBJECTIVE_NAME, "objective name");
        String criteria = readText(in, 128, "criteria");
        String display = readTextAllowEmpty(in, MAX_TEXT, "objective display name");
        int render = in.readUnsignedByte();
        if (render >= RenderType.values().length) throw new IOException("Invalid render type " + render);
        Objective objective = new Objective(name, criteria, display, RenderType.values()[render]);
        objective.displayAutoUpdate = in.readBoolean();
        objective.numberFormat = readFormat(in);
        return objective;
    }

    private static void writeScore(DataOutput out, Score score) throws IOException {
        out.writeInt(score.value);
        out.writeBoolean(score.locked);
        writeNullable(out, score.displayName);
        writeFormat(out, score.numberFormat);
    }

    private static Score readScore(DataInput in) throws IOException {
        Score score = new Score(in.readInt());
        score.locked = in.readBoolean();
        score.displayName = readNullable(in, MAX_TEXT);
        score.numberFormat = readFormat(in);
        return score;
    }

    private static void writeTeam(DataOutput out, Team team) throws IOException {
        out.writeUTF(team.name);
        out.writeUTF(team.displayName);
        out.writeByte(team.color);
        out.writeBoolean(team.friendlyFire);
        out.writeBoolean(team.seeFriendlyInvisibles);
        out.writeUTF(team.prefix);
        out.writeUTF(team.suffix);
        out.writeUTF(team.nameTagVisibility);
        out.writeUTF(team.deathMessageVisibility);
        out.writeUTF(team.collisionRule);
        out.writeInt(team.players.size());
        for (String player : team.players) out.writeUTF(player);
    }

    private static Team readTeam(DataInput in) throws IOException {
        String name = readText(in, MAX_TEAM_NAME, "team name");
        Team team = new Team(name, readTextAllowEmpty(in, MAX_TEXT, "team display name"));
        team.color = in.readByte();
        if (team.color < -1 || team.color > 15) throw new IOException("Invalid team color " + team.color);
        team.friendlyFire = in.readBoolean();
        team.seeFriendlyInvisibles = in.readBoolean();
        team.prefix = readTextAllowEmpty(in, MAX_TEXT, "team prefix");
        team.suffix = readTextAllowEmpty(in, MAX_TEXT, "team suffix");
        try {
            team.nameTagVisibility = normalizeVisibility(readText(in, 32, "name tag visibility"));
            team.deathMessageVisibility = normalizeVisibility(readText(in, 32, "death visibility"));
            team.collisionRule = normalizeCollision(readText(in, 32, "collision rule"));
        } catch (IllegalArgumentException invalid) {
            throw new IOException(invalid.getMessage());
        }
        int members = checkedCount(in.readInt(), MAX_TEAM_MEMBERS, "team members");
        for (int i = 0; i < members; ++i) team.players.add(readText(in, MAX_HOLDER_NAME, "team member"));
        return team;
    }

    private static void writeFormat(DataOutput out, NumberFormat format) throws IOException {
        NumberFormat actual = format == null ? NumberFormat.defaultFormat() : format;
        out.writeByte(actual.type.ordinal());
        out.writeUTF(actual.value);
    }

    private static NumberFormat readFormat(DataInput in) throws IOException {
        int type = in.readUnsignedByte();
        if (type >= FormatType.values().length) throw new IOException("Invalid number format " + type);
        String value = readTextAllowEmpty(in, MAX_TEXT, "number format");
        return new NumberFormat(FormatType.values()[type], value);
    }

    private static void writeNullable(DataOutput out, String value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) out.writeUTF(value);
    }

    private static String readNullable(DataInput in, int max) throws IOException {
        return in.readBoolean() ? readTextAllowEmpty(in, max, "optional text") : null;
    }

    private static String readText(DataInput in, int max, String label) throws IOException {
        String value = in.readUTF();
        if (value.length() == 0 || value.length() > max) throw new IOException("Invalid " + label);
        return value;
    }

    private static String readTextAllowEmpty(DataInput in, int max, String label) throws IOException {
        String value = in.readUTF();
        if (value.length() > max) throw new IOException("Invalid " + label);
        return value;
    }
}
