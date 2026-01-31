package com.legacyminecraft.poseidon.util;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * A wrapper class for the Minecraft session API
 * <p>
 * TODO maybe make the HTTP requests asynchronous? idk if it really matters
 *
 * @author moderator_man
 */
public class SessionAPI {
    public static final String SESSION_BASE = "http://session.minecraft.net/game/";
    public static final String MODERN_SESSION_BASE = "https://sessionserver.mojang.com/session/minecraft/";

    public static boolean hasJoined(String username, String serverId) {
        HTTPResponse response = httpGetRequest(SESSION_BASE + String.format("checkserver.jsp?user=%s&serverId=%s", username, serverId));
        if (!"YES".equals(response.getResponse())) return false;
        return true;
    }

    public static void hasJoined(String username, String serverId, String ip, SessionRequestRunnable callback) {
        try {
            boolean checkIP = !"127.0.0.1".equals(ip) && !"localhost".equals(ip);
            StringBuilder sb = new StringBuilder();
            sb.append(MODERN_SESSION_BASE + "hasJoined");
            sb.append("?username=" + username);
            sb.append("&serverId=" + serverId);
            if (checkIP) sb.append("&ip=" + ip);
            String requestUrl = sb.toString();

            HTTPResponse response = httpGetRequest(requestUrl);

            // Handle 204 No Content
            if (response.getResponseCode() == 204 || response.getResponse().isEmpty()) {
                callback.callback(204, "", "", "");
                return;
            }

            JSONObject obj = (JSONObject) new JSONParser().parse(response.getResponse());
            String res_username = (obj.containsKey("name") ? (String) obj.get("name") : "nousername");
            String res_uuid = (obj.containsKey("id") ? (String) obj.get("id") : "nouuid");
            String res_ip = (obj.containsKey("ip") ? (String) obj.get("ip") : "noip");
            System.out.println("[AUTH] Mojang response for " + username + ": code=" + response.getResponseCode() + ", uuid=" + res_uuid);
            callback.callback(response.getResponseCode(), res_username, res_uuid, res_ip);
        } catch (Exception ex) {
            System.out.println(String.format("[AUTH] Failed to authenticate session for '%s': %s", username, ex.getMessage()));
            ex.printStackTrace();
            callback.callback(-1, "", "", "");
        }
    }

    private static HTTPResponse httpGetRequest(String url) {
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (url.startsWith("https") ? (HttpsURLConnection) obj.openConnection() : (HttpURLConnection) obj.openConnection());
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", "Project-Poseidon/1.0");
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);

            int responseCode = con.getResponseCode();

            if (responseCode == 204) {
                return new HTTPResponse("", responseCode);
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();
            while ((inputLine = in.readLine()) != null) { response.append(inputLine); }
            in.close();
            return new HTTPResponse(response.toString(), responseCode);
        } catch (Exception ex) {
            ex.printStackTrace();
            return new HTTPResponse("", -1);
        }
    }
}
