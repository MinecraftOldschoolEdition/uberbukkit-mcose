package net.minecraft.server.network;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ServerRulesProtocolTest {
    @Test
    public void showPayloadRoundTripsAConsentScreen() {
        byte[] payload = ServerRulesProtocol.createShowPayload(ServerRulesProtocol.SCREEN_CONSENT,
                Arrays.asList("Be kind.", "No exploits."));

        ServerRulesProtocol.ScreenData data = ServerRulesProtocol.readShowPayload(payload);

        assertNotNull(data);
        assertEquals(ServerRulesProtocol.SCREEN_CONSENT, data.screen);
        assertEquals(Arrays.asList("Be kind.", "No exploits."), data.rules);
    }

    @Test
    public void savePayloadTrimsBlankEditorRows() {
        byte[] payload = ServerRulesProtocol.createActionPayload(ServerRulesProtocol.ACTION_SAVE,
                Arrays.asList(" First rule ", "", "Second rule"));

        ServerRulesProtocol.ActionData data = ServerRulesProtocol.readActionPayload(payload);

        assertNotNull(data);
        assertEquals(ServerRulesProtocol.ACTION_SAVE, data.action);
        assertEquals(Arrays.asList("First rule", "Second rule"), data.rules);
    }

    @Test
    public void rejectsUnexpectedTrailingBytes() {
        byte[] payload = ServerRulesProtocol.createActionPayload(ServerRulesProtocol.ACTION_AGREE,
                Collections.<String>emptyList());
        byte[] malformed = Arrays.copyOf(payload, payload.length + 1);

        assertNull(ServerRulesProtocol.readActionPayload(malformed));
        assertFalse(ServerRulesProtocol.normalizeRules(Collections.singletonList("line\nfeed"), true) != null);
    }
}
