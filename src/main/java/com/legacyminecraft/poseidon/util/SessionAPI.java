package com.legacyminecraft.poseidon.util;

import net.minecraft.server.MinecraftServer;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A wrapper class for the Minecraft session API
 * <p>
 * TODO maybe make the HTTP requests asynchronous? idk if it really matters
 *
 * @author moderator_man
 */
public class SessionAPI {
    public static final String MODERN_SESSION_BASE = "https://sessionserver.mojang.com/session/minecraft/";
    private static final int HTTP_TIMEOUT_MS = 3000;
    private static final int MAX_RESPONSE_CHARS = 32768;
    private static final long FAILURE_LOG_INTERVAL_MS = 30000L;
    private static final AtomicLong LAST_FAILURE_LOG_AT = new AtomicLong(0L);
    private static final AtomicLong SUPPRESSED_FAILURE_LOGS = new AtomicLong(0L);

    public static class ModernSessionResponse {
        private final int responseCode;
        private final String username;
        private final String uuid;
        private final UUID profileUuid;
        private final String ip;

        public ModernSessionResponse(int responseCode, String username, String uuid, String ip) {
            this.responseCode = responseCode;
            this.username = username;
            this.uuid = uuid;
            this.profileUuid = parseProfileUuid(uuid);
            this.ip = ip;
        }

        public int getResponseCode() {
            return this.responseCode;
        }

        public String getUsername() {
            return this.username;
        }

        public String getUuid() {
            return this.uuid;
        }

        public UUID getProfileUuid() {
            return this.profileUuid;
        }

        public String getIp() {
            return this.ip;
        }
    }

    @Deprecated
    public static boolean hasJoined(String username, String serverId) {
        ModernSessionResponse response = hasJoinedModern(username, serverId, null);
        return response.getResponseCode() == HttpURLConnection.HTTP_OK
                && username != null
                && username.equalsIgnoreCase(response.getUsername())
                && response.getProfileUuid() != null;
    }

    public static void hasJoined(String username, String serverId, String ip, SessionRequestRunnable callback) {
        ModernSessionResponse result = hasJoinedModern(username, serverId, ip);
        callback.callback(result.getResponseCode(), result.getUsername(), result.getUuid(), result.getIp());
    }

    public static ModernSessionResponse hasJoinedModern(String username, String serverId, String ip) {
        try {
            HTTPResponse response = httpGetRequest(buildHasJoinedRequestUrl(username, serverId, ip));

            // Handle 204 No Content
            if (response.getResponseCode() == 204) {
                return new ModernSessionResponse(204, "", "", "");
            }

            if (response.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return new ModernSessionResponse(response.getResponseCode(), "", "", "");
            }

            ModernSessionResponse result = parseHasJoinedResponse(response.getResponseCode(), response.getResponse());
            MinecraftServer.log.fine("[AUTH] Mojang response for " + username + ": code=" + response.getResponseCode());
            return result;
        } catch (Exception ex) {
            logLookupFailure(ex);
            return new ModernSessionResponse(-1, "", "", "");
        }
    }

    static String buildHasJoinedRequestUrl(String username, String serverId, String ip) throws IOException {
        if (username == null || username.isEmpty() || serverId == null || serverId.isEmpty()) {
            throw new IOException("Missing hasJoined request parameter");
        }

        StringBuilder request = new StringBuilder(MODERN_SESSION_BASE).append("hasJoined");
        request.append("?username=").append(encodeQueryValue(username));
        request.append("&serverId=").append(encodeQueryValue(serverId));
        if (ip != null && !ip.isEmpty()) {
            request.append("&ip=").append(encodeQueryValue(ip));
        }
        return request.toString();
    }

    static ModernSessionResponse parseHasJoinedResponse(int responseCode, String responseBody) throws Exception {
        if (responseCode != HttpURLConnection.HTTP_OK) {
            return new ModernSessionResponse(responseCode, "", "", "");
        }
        if (responseBody == null || responseBody.isEmpty()) {
            throw new IOException("Empty successful hasJoined response");
        }

        Object parsed = new JSONParser().parse(responseBody);
        if (!(parsed instanceof JSONObject)) {
            throw new IOException("Invalid hasJoined response object");
        }

        JSONObject object = (JSONObject) parsed;
        Object nameValue = object.get("name");
        Object idValue = object.get("id");
        if (!(nameValue instanceof String) || ((String) nameValue).isEmpty()) {
            throw new IOException("Missing hasJoined profile name");
        }
        if (!(idValue instanceof String) || parseProfileUuid((String) idValue) == null) {
            throw new IOException("Invalid hasJoined profile UUID");
        }

        Object ipValue = object.get("ip");
        String responseIp = ipValue instanceof String ? (String) ipValue : "";
        return new ModernSessionResponse(responseCode, (String) nameValue, (String) idValue, responseIp);
    }

    private static String encodeQueryValue(String value) throws IOException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    static UUID parseProfileUuid(String compactUuid) {
        if (compactUuid == null || !compactUuid.matches("[0-9a-fA-F]{32}")) {
            return null;
        }
        try {
            return UUID.fromString(compactUuid.substring(0, 8)
                    + "-" + compactUuid.substring(8, 12)
                    + "-" + compactUuid.substring(12, 16)
                    + "-" + compactUuid.substring(16, 20)
                    + "-" + compactUuid.substring(20, 32));
        } catch (IllegalArgumentException invalidUuid) {
            return null;
        }
    }

    private static void logLookupFailure(Exception exception) {
        long now = System.currentTimeMillis();
        long previous = LAST_FAILURE_LOG_AT.get();
        if (now - previous >= FAILURE_LOG_INTERVAL_MS && LAST_FAILURE_LOG_AT.compareAndSet(previous, now)) {
            long suppressed = SUPPRESSED_FAILURE_LOGS.getAndSet(0L);
            String reason = exception == null || exception.getMessage() == null
                    ? "unknown error"
                    : exception.getMessage().replace('\r', ' ').replace('\n', ' ').trim();
            if (reason.length() > 160) {
                reason = reason.substring(0, 157) + "...";
            }
            MinecraftServer.log.warning("[AUTH] Mojang session lookup failed: " + reason
                    + (suppressed > 0L ? " (" + suppressed + " similar failures suppressed)" : ""));
        } else {
            SUPPRESSED_FAILURE_LOGS.incrementAndGet();
        }
    }

    public static boolean isRetryableStatusCode(int responseCode) {
        return responseCode == -1
                || responseCode == 408
                || responseCode == 429
                || responseCode == 500
                || responseCode == 502
                || responseCode == 503
                || responseCode == 504;
    }

    private static HTTPResponse httpGetRequest(String url) {
        HttpsURLConnection connection = null;
        try {
            URL endpoint = new URL(url);
            if (!"https".equalsIgnoreCase(endpoint.getProtocol())
                    || !"sessionserver.mojang.com".equalsIgnoreCase(endpoint.getHost())
                    || endpoint.getPort() != -1
                    || endpoint.getUserInfo() != null) {
                throw new IOException("Refusing untrusted Mojang session endpoint");
            }

            connection = (HttpsURLConnection) endpoint.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Project-Poseidon/1.0");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(HTTP_TIMEOUT_MS);
            connection.setReadTimeout(HTTP_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);

            int responseCode = connection.getResponseCode();

            if (responseCode == 204) {
                return new HTTPResponse("", responseCode);
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return new HTTPResponse("", responseCode);
            }

            InputStream stream = connection.getInputStream();
            if (stream == null) {
                throw new IOException("Missing hasJoined response stream");
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                char[] buffer = new char[2048];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    if (response.length() + read > MAX_RESPONSE_CHARS) {
                        throw new IOException("Oversized hasJoined response");
                    }
                    response.append(buffer, 0, read);
                }
            }
            return new HTTPResponse(response.toString(), responseCode);
        } catch (Exception ex) {
            logLookupFailure(ex);
            return new HTTPResponse("", -1);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
