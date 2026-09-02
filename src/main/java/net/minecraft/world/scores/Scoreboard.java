package net.minecraft.world.scores;

import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.server.scoreboard.ModernScoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 26.3-named server facade. Text components are represented by legacy strings,
 * but class names and mutation entry points follow the current Mojang model.
 */
public class Scoreboard {
    public static final String HIDDEN_SCORE_PREFIX = "#";
    private final ModernScoreboard delegate;
    private final Map<ModernScoreboard.Objective, Objective> objectiveWrappers =
        new IdentityHashMap<ModernScoreboard.Objective, Objective>();
    private final Map<ModernScoreboard.Team, PlayerTeam> teamWrappers =
        new IdentityHashMap<ModernScoreboard.Team, PlayerTeam>();

    public Scoreboard(ModernScoreboard delegate) {
        if(delegate == null) throw new IllegalArgumentException("scoreboard");
        this.delegate = delegate;
    }

    ModernScoreboard delegate() { return this.delegate; }

    public Objective getObjective(String name) { return wrap(this.delegate.getObjective(name)); }

    public Objective addObjective(String name, ObjectiveCriteria criteria, String displayName,
                                  ObjectiveCriteria.RenderType renderType, boolean displayAutoUpdate,
                                  NumberFormat numberFormat) {
        if(criteria == null) criteria = ObjectiveCriteria.DUMMY;
        if(renderType == null) renderType = criteria.getDefaultRenderType();
        ModernScoreboard.Objective added = this.delegate.addObjective(name, criteria.getName(), displayName);
        this.delegate.updateObjective(added, null, ModernScoreboard.RenderType.byName(renderType.getId()),
            Boolean.valueOf(displayAutoUpdate), null);
        this.delegate.setObjectiveNumberFormat(added, toInternalFormat(numberFormat));
        return wrap(added);
    }

    public final void forAllObjectives(ObjectiveCriteria criteria, ScoreHolder holder, Consumer<ScoreAccess> operation) {
        if(criteria == null || holder == null || operation == null) return;
        for(Objective objective : getObjectives()) {
            if(criteria.getName().equals(objective.getCriteria().getName())) {
                operation.accept(getOrCreatePlayerScore(holder, objective, true));
            }
        }
    }

    public ScoreAccess getOrCreatePlayerScore(ScoreHolder holder, Objective objective) {
        return getOrCreatePlayerScore(holder, objective, false);
    }

    public ScoreAccess getOrCreatePlayerScore(final ScoreHolder holder, final Objective objective,
                                              final boolean forceWritable) {
        requireObjective(objective);
        this.delegate.getOrCreateScore(holder.getScoreboardName(), objective.handle, true);
        return new ScoreAccess() {
            private ModernScoreboard.Score score() {
                return delegate.getScore(holder.getScoreboardName(), objective.getName());
            }
            public int get() { return score().value; }
            public int value() { return get(); }
            public void set(int value) {
                if(!forceWritable && objective.getCriteria().isReadOnly()) {
                    throw new IllegalStateException("Cannot modify read-only score");
                }
                delegate.setScore(holder.getScoreboardName(), objective.handle, value, true);
                if(objective.displayAutoUpdate()) {
                    String displayName = holder.getDisplayName();
                    if(displayName != null) {
                        delegate.setScoreDisplay(holder.getScoreboardName(), objective.handle, displayName);
                    }
                }
            }
            public String display() { return score().displayName; }
            public void display(String value) { delegate.setScoreDisplay(holder.getScoreboardName(), objective.handle, value); }
            public NumberFormat numberFormat() { return fromInternalFormat(score().numberFormat); }
            public void numberFormatOverride(NumberFormat format) {
                delegate.setScoreNumberFormat(holder.getScoreboardName(), objective.handle, toInternalFormat(format));
            }
            public boolean locked() { return score().locked; }
            public boolean isLocked() { return locked(); }
            public void unlock() { delegate.setScoreLocked(holder.getScoreboardName(), objective.handle, false); }
            public void lock() { delegate.setScoreLocked(holder.getScoreboardName(), objective.handle, true); }
        };
    }

    public ReadOnlyScoreInfo getPlayerScoreInfo(final ScoreHolder holder, final Objective objective) {
        requireObjective(objective);
        final ModernScoreboard.Score score = this.delegate.getScore(holder.getScoreboardName(), objective.getName());
        if(score == null) return null;
        return new ReadOnlyScoreInfo() {
            public int value() { return score.value; }
            public boolean isLocked() { return score.locked; }
            public NumberFormat numberFormat() { return fromInternalFormat(score.numberFormat); }
        };
    }

    public Collection<PlayerScoreEntry> listPlayerScores(Objective objective) {
        requireObjective(objective);
        List<PlayerScoreEntry> result = new ArrayList<PlayerScoreEntry>();
        for(ModernScoreboard.ScoreEntry entry : this.delegate.getScores(objective.handle)) {
            result.add(new PlayerScoreEntry(entry.owner, entry.score.value, entry.score.displayName,
                fromInternalFormat(entry.score.numberFormat)));
        }
        return result;
    }

    public Collection<Objective> getObjectives() {
        List<Objective> result = new ArrayList<Objective>();
        for(ModernScoreboard.Objective objective : this.delegate.getObjectives()) result.add(wrap(objective));
        return result;
    }
    public Collection<String> getObjectiveNames() {
        List<String> result = new ArrayList<String>();
        for(ModernScoreboard.Objective objective : this.delegate.getObjectives()) result.add(objective.name);
        return result;
    }
    public Collection<ScoreHolder> getTrackedPlayers() {
        List<ScoreHolder> result = new ArrayList<ScoreHolder>();
        for(String holder : this.delegate.getTrackedHolders()) result.add(ScoreHolder.forNameOnly(holder));
        return result;
    }
    public void resetAllPlayerScores(ScoreHolder holder) { this.delegate.resetScore(holder.getScoreboardName(), null); }
    public void resetSinglePlayerScore(ScoreHolder holder, Objective objective) {
        requireObjective(objective); this.delegate.resetScore(holder.getScoreboardName(), objective.getName());
    }
    public Map<Objective, Integer> listPlayerScores(ScoreHolder holder) {
        Map<Objective, Integer> result = new LinkedHashMap<Objective, Integer>();
        for(ModernScoreboard.ScoreEntry entry : this.delegate.getScores(holder.getScoreboardName())) {
            result.put(wrap(entry.objective), Integer.valueOf(entry.score.value));
        }
        return result;
    }
    public void removeObjective(Objective objective) { requireObjective(objective); this.delegate.removeObjective(objective.getName()); }
    public void setDisplayObjective(DisplaySlot slot, Objective objective) {
        if(objective != null) requireObjective(objective);
        this.delegate.setDisplayObjective(slot.id(), objective == null ? null : objective.getName());
    }
    public Objective getDisplayObjective(DisplaySlot slot) { return wrap(this.delegate.getDisplayObjective(slot.id())); }
    public PlayerTeam getPlayerTeam(String name) { return wrap(this.delegate.getTeam(name)); }
    public PlayerTeam addPlayerTeam(String name) {
        ModernScoreboard.Team existing = this.delegate.getTeam(name);
        return wrap(existing == null ? this.delegate.addTeam(name, name) : existing);
    }
    public void removePlayerTeam(PlayerTeam team) { requireTeam(team); this.delegate.removeTeam(team.getName()); }
    public boolean addPlayerToTeam(String player, PlayerTeam team) { requireTeam(team); return this.delegate.joinTeam(player, team.handle); }
    public boolean removePlayerFromTeam(String player) { return this.delegate.leaveTeam(player); }
    public void removePlayerFromTeam(String player, PlayerTeam team) {
        requireTeam(team);
        if(this.delegate.getPlayersTeam(player) != team.handle) throw new IllegalStateException("Player is not on team " + team.getName());
        this.delegate.leaveTeam(player);
    }
    public Collection<String> getTeamNames() {
        List<String> result = new ArrayList<String>();
        for(ModernScoreboard.Team team : this.delegate.getTeams()) result.add(team.name);
        return result;
    }
    public Collection<PlayerTeam> getPlayerTeams() {
        List<PlayerTeam> result = new ArrayList<PlayerTeam>();
        for(ModernScoreboard.Team team : this.delegate.getTeams()) result.add(wrap(team));
        return result;
    }
    public PlayerTeam getPlayersTeam(String player) { return wrap(this.delegate.getPlayersTeam(player)); }

    /** Mirrors 26.3 cleanup: dead non-player holders do not leave scores or team membership behind. */
    public void entityRemoved(net.minecraft.server.Entity entity) {
        if(entity == null || entity instanceof net.minecraft.server.EntityPlayer || !entity.dead) return;
        ScoreHolder holder = entity;
        resetAllPlayerScores(holder);
        removePlayerFromTeam(holder.getScoreboardName());
    }

    public void onObjectiveAdded(Objective objective) {}
    public void onObjectiveChanged(Objective objective) {}
    public void onObjectiveRemoved(Objective objective) {}
    public void onPlayerRemoved(ScoreHolder holder) {}
    public void onPlayerScoreRemoved(ScoreHolder holder, Objective objective) {}
    public void onTeamAdded(PlayerTeam team) {}
    public void onTeamChanged(PlayerTeam team) { if(team != null) team.changed(); }
    public void onTeamRemoved(PlayerTeam team) {}

    private Objective wrap(ModernScoreboard.Objective handle) {
        if(handle == null) return null;
        Objective wrapper = this.objectiveWrappers.get(handle);
        if(wrapper == null) { wrapper = new Objective(this, handle); this.objectiveWrappers.put(handle, wrapper); }
        return wrapper;
    }
    private PlayerTeam wrap(ModernScoreboard.Team handle) {
        if(handle == null) return null;
        PlayerTeam wrapper = this.teamWrappers.get(handle);
        if(wrapper == null) { wrapper = new PlayerTeam(this, handle); this.teamWrappers.put(handle, wrapper); }
        return wrapper;
    }
    private void requireObjective(Objective objective) {
        if(objective == null || objective.getScoreboard() != this || this.delegate.getObjective(objective.getName()) != objective.handle) {
            throw new IllegalArgumentException("Objective does not belong to this scoreboard");
        }
    }
    private void requireTeam(PlayerTeam team) {
        if(team == null || team.getScoreboard() != this || this.delegate.getTeam(team.getName()) != team.handle) {
            throw new IllegalArgumentException("Team does not belong to this scoreboard");
        }
    }

    static ModernScoreboard.NumberFormat toInternalFormat(NumberFormat format) {
        if(format == null) return ModernScoreboard.NumberFormat.defaultFormat();
        if(format instanceof BlankFormat) return ModernScoreboard.NumberFormat.blank();
        if(format instanceof FixedFormat) return ModernScoreboard.NumberFormat.fixed(((FixedFormat)format).value());
        if(format instanceof StyledFormat) return ModernScoreboard.NumberFormat.styled(((StyledFormat)format).style());
        return ModernScoreboard.NumberFormat.fixed(format.format(0));
    }
    static NumberFormat fromInternalFormat(ModernScoreboard.NumberFormat format) {
        if(format == null || format.type == ModernScoreboard.FormatType.DEFAULT) return null;
        if(format.type == ModernScoreboard.FormatType.BLANK) return BlankFormat.INSTANCE;
        if(format.type == ModernScoreboard.FormatType.FIXED) return new FixedFormat(format.value);
        return new StyledFormat(format.value);
    }
}
