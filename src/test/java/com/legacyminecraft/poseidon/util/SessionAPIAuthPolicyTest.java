package com.legacyminecraft.poseidon.util;

import org.junit.Test;

import java.net.HttpURLConnection;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SessionAPIAuthPolicyTest {

    @Test
    public void encodesEveryHasJoinedQueryValueAndOmitsOptionalIp() throws Exception {
        String request = SessionAPI.buildHasJoinedRequestUrl("Player&admin=true", "-hash+value", null);

        assertEquals(
                "https://sessionserver.mojang.com/session/minecraft/hasJoined"
                        + "?username=Player%26admin%3Dtrue&serverId=-hash%2Bvalue",
                request);
        assertTrue(!request.contains("&ip="));
    }

    @Test
    public void encodesIpv6WhenIpBindingIsRequested() throws Exception {
        String request = SessionAPI.buildHasJoinedRequestUrl("Player", "abc", "2001:db8::7");

        assertTrue(request.endsWith("&ip=2001%3Adb8%3A%3A7"));
    }

    @Test
    public void parsesAndValidatesAuthenticatedProfileUuid() throws Exception {
        SessionAPI.ModernSessionResponse response = SessionAPI.parseHasJoinedResponse(
                HttpURLConnection.HTTP_OK,
                "{\"id\":\"853c80ef3c3749fdaa49938b674adae6\",\"name\":\"jeb_\",\"properties\":[]}");

        assertEquals("jeb_", response.getUsername());
        assertEquals(UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6"), response.getProfileUuid());
    }

    @Test
    public void rejectsSuccessfulResponseWithoutAValidProfileUuid() throws Exception {
        assertInvalidResponse("{\"name\":\"jeb_\"}");
        assertInvalidResponse("{\"id\":\"not-a-uuid\",\"name\":\"jeb_\"}");
        assertInvalidResponse("[]");
    }

    @Test
    public void noContentRemainsARejectedSessionRatherThanAProfile() throws Exception {
        SessionAPI.ModernSessionResponse response = SessionAPI.parseHasJoinedResponse(204, "");

        assertEquals(204, response.getResponseCode());
        assertNull(response.getProfileUuid());
    }

    private static void assertInvalidResponse(String responseBody) throws Exception {
        try {
            SessionAPI.parseHasJoinedResponse(HttpURLConnection.HTTP_OK, responseBody);
            fail("Expected malformed hasJoined response rejection");
        } catch (Exception expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
