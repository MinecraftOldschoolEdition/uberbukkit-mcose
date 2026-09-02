package net.minecraft.server.scoreboard;

import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ModernScoreboardParityTest {
    @Test
    public void displaySlotsMatch26_3NamesAndScoreOrder() {
        assertEquals(19, ModernScoreboard.DISPLAY_SLOT_NAMES.length);
        String[] names = new String[DisplaySlot.values().length];
        for(int i = 0; i < names.length; ++i) names[i] = DisplaySlot.values()[i].getSerializedName();
        assertArrayEquals(ModernScoreboard.DISPLAY_SLOT_NAMES, names);

        ModernScoreboard board = new ModernScoreboard();
        ModernScoreboard.Objective objective = board.addObjective("points", "dummy", "Points");
        board.setScore("beta", objective, 3, false);
        board.setScore("Alpha", objective, 8, false);
        board.setScore("#hidden", objective, 99, false);
        List<ModernScoreboard.ScoreEntry> scores = board.getScores(objective);
        assertEquals("#hidden", scores.get(0).owner);
        assertEquals("Alpha", scores.get(1).owner);
        assertTrue(scores.get(0).isHidden());
    }

    @Test
    public void serializationPreservesFormatsTriggersTeamsAndRules() throws Exception {
        ModernScoreboard board = new ModernScoreboard();
        ModernScoreboard.Objective objective = board.addObjective("triggered", "trigger", "Trigger");
        board.setObjectiveNumberFormat(objective, ModernScoreboard.NumberFormat.blank());
        board.setDisplayObjective(1, objective.name);
        board.setScore("Alice", objective, 17, false);
        board.setScoreLocked("Alice", objective, false);
        board.setScoreDisplay("Alice", objective, "Shown Alice");
        ModernScoreboard.Team team = board.addTeam("red", "Red Team");
        team.color = 12;
        team.friendlyFire = false;
        team.nameTagVisibility = "hideForOtherTeams";
        team.collisionRule = "pushOwnTeam";
        board.updateTeam(team);
        board.joinTeam("Alice", team);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        board.write(new DataOutputStream(bytes));
        ModernScoreboard loaded = ModernScoreboard.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));

        assertEquals("triggered", loaded.getDisplayObjective(1).name);
        assertEquals(17, loaded.getScore("Alice", "triggered").value);
        assertFalse(loaded.getScore("Alice", "triggered").locked);
        assertEquals(ModernScoreboard.FormatType.BLANK, loaded.getObjective("triggered").numberFormat.type);
        assertFalse(loaded.allowsFriendlyFire("Alice", "Alice"));
        assertTrue(loaded.allowsCollision("Alice", "Alice"));
        assertFalse(loaded.allowsCollision("Alice", "Bob"));
    }

    @Test
    public void namedFacadeMutatesTheAuthoritativeModel() {
        ModernScoreboard internal = new ModernScoreboard();
        net.minecraft.world.scores.Scoreboard scoreboard = new net.minecraft.world.scores.Scoreboard(internal);
        net.minecraft.world.scores.Objective objective = scoreboard.addObjective(
            "ported", ObjectiveCriteria.TRIGGER, "Ported", ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);
        ScoreAccess score = scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly("Alice"), objective);
        score.set(4);
        score.unlock();
        scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, objective);

        assertNotNull(scoreboard.getObjective("ported"));
        assertEquals(4, internal.getScore("Alice", "ported").value);
        assertFalse(internal.getScore("Alice", "ported").locked);
        assertEquals("ported", internal.getDisplayObjective(1).name);
    }

    @Test
    public void criteriaRegistryUses26_3OptionalLookupAndExactTeamColorNames() {
        assertEquals(ObjectiveCriteria.DUMMY, ObjectiveCriteria.byName("dummy").get());
        assertTrue(ObjectiveCriteria.byName("teamkill.red").isPresent());
        assertFalse(ObjectiveCriteria.byName("teamkill.not_a_color").isPresent());
        assertTrue(ObjectiveCriteria.byName("minecraft.custom:minecraft.jump").isPresent());
    }
}
