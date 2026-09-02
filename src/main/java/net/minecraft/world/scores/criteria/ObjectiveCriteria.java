package net.minecraft.world.scores.criteria;

import net.minecraft.world.scores.TeamColor;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ObjectiveCriteria {
    private static final Map<String, ObjectiveCriteria> CUSTOM = new LinkedHashMap<String, ObjectiveCriteria>();
    public static final ObjectiveCriteria DUMMY = register("dummy", false, RenderType.INTEGER);
    public static final ObjectiveCriteria TRIGGER = register("trigger", false, RenderType.INTEGER);
    public static final ObjectiveCriteria DEATH_COUNT = register("deathCount", false, RenderType.INTEGER);
    public static final ObjectiveCriteria KILL_COUNT_PLAYERS = register("playerKillCount", false, RenderType.INTEGER);
    public static final ObjectiveCriteria KILL_COUNT_ALL = register("totalKillCount", false, RenderType.INTEGER);
    public static final ObjectiveCriteria HEALTH = register("health", true, RenderType.HEARTS);
    public static final ObjectiveCriteria FOOD = register("food", true, RenderType.INTEGER);
    public static final ObjectiveCriteria AIR = register("air", true, RenderType.INTEGER);
    public static final ObjectiveCriteria ARMOR = register("armor", true, RenderType.INTEGER);
    public static final ObjectiveCriteria EXPERIENCE = register("xp", true, RenderType.INTEGER);
    public static final ObjectiveCriteria LEVEL = register("level", true, RenderType.INTEGER);
    public static final Map<TeamColor, ObjectiveCriteria> TEAM_KILL = teamCriteria("teamkill.");
    public static final Map<TeamColor, ObjectiveCriteria> KILLED_BY_TEAM = teamCriteria("killedByTeam.");
    private final String name;
    private final boolean readOnly;
    private final RenderType renderType;

    protected ObjectiveCriteria(String name) {
        this(name, false, RenderType.INTEGER);
    }

    protected ObjectiveCriteria(String name, boolean readOnly, RenderType renderType) {
        this.name = name;
        this.readOnly = readOnly;
        this.renderType = renderType;
    }

    private static ObjectiveCriteria register(String name, boolean readOnly, RenderType renderType) {
        ObjectiveCriteria criteria = new ObjectiveCriteria(name, readOnly, renderType);
        CUSTOM.put(name, criteria);
        return criteria;
    }

    private static Map<TeamColor, ObjectiveCriteria> teamCriteria(String prefix) {
        Map<TeamColor, ObjectiveCriteria> result = new EnumMap<TeamColor, ObjectiveCriteria>(TeamColor.class);
        for(TeamColor color : TeamColor.values()) result.put(color, register(prefix + color.getSerializedName(), false, RenderType.INTEGER));
        return Collections.unmodifiableMap(result);
    }

    public String getName() { return this.name; }
    public boolean isReadOnly() { return this.readOnly; }
    public RenderType getDefaultRenderType() { return this.renderType; }
    public static Set<String> getCustomCriteriaNames() { return Collections.unmodifiableSet(CUSTOM.keySet()); }
    public static Optional<ObjectiveCriteria> byName(String name) {
        ObjectiveCriteria known = CUSTOM.get(name);
        if(known != null) return Optional.of(known);
        if(name == null || name.indexOf(':') < 0) return Optional.empty();
        ObjectiveCriteria criteria = new ObjectiveCriteria(name, true, RenderType.INTEGER);
        CUSTOM.put(name, criteria);
        return Optional.of(criteria);
    }

    public enum RenderType {
        INTEGER("integer"), HEARTS("hearts");
        private final String id;
        RenderType(String id) { this.id = id; }
        public String getId() { return this.id; }
        public String getSerializedName() { return this.id; }
        public static RenderType byId(String id) { return "hearts".equalsIgnoreCase(id) ? HEARTS : INTEGER; }
    }
}
