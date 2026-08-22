package net.minecraft.server.serverdirectory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/** Bounded HTTPS client used only from the server-directory worker pool. */
public class ServerDirectoryWebsiteClient {
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int MAX_RESPONSE_BYTES = 65536;
    private static final int MAX_REQUEST_BYTES = 8192;
    private static final Gson GSON = new Gson();

    public ServerDirectoryProtocol.Listing getListing(ServerDirectoryConfig config) throws IOException, WebsiteException {
        JsonObject body = execute(config, "GET", listingPath(config), null);
        JsonElement listing = body.get("listing");
        return listing == null || listing.isJsonNull() ? null : parseListing(listing.getAsJsonObject());
    }

    public ServerDirectoryProtocol.Listing putListing(ServerDirectoryConfig config,
                                                      ServerDirectoryProtocol.Mutation mutation,
                                                      String creatorUsername,
                                                      String creatorUuid) throws IOException, WebsiteException {
        JsonObject body = new JsonObject();
        if (mutation.expectedRevision > 0) body.addProperty("expectedRevision", mutation.expectedRevision);
        else body.add("expectedRevision", com.google.gson.JsonNull.INSTANCE);
        body.addProperty("name", mutation.name);
        body.addProperty("host", config.host);
        body.addProperty("port", config.port);
        JsonObject creator = new JsonObject();
        creator.addProperty("username", creatorUsername);
        if (creatorUuid != null && creatorUuid.length() > 0) creator.addProperty("uuid", creatorUuid);
        body.add("creator", creator);
        body.addProperty("description", mutation.description);
        JsonArray tags = new JsonArray();
        for (String tag : mutation.tagIds) tags.add(tag);
        body.add("tagIds", tags);
        JsonObject response = execute(config, "PUT", "/api/servers/listing", body);
        return parseListing(response.getAsJsonObject("listing"));
    }

    public void deleteListing(ServerDirectoryConfig config, int expectedRevision) throws IOException, WebsiteException {
        JsonObject body = new JsonObject();
        body.addProperty("host", config.host);
        body.addProperty("port", config.port);
        body.addProperty("expectedRevision", expectedRevision);
        execute(config, "DELETE", "/api/servers/listing", body);
    }

    private static String listingPath(ServerDirectoryConfig config) throws IOException {
        return "/api/servers/listing?host=" + URLEncoder.encode(config.host, "UTF-8") + "&port=" + config.port;
    }

    private JsonObject execute(ServerDirectoryConfig config, String method, String path, JsonObject requestBody)
            throws IOException, WebsiteException {
        byte[] encoded = requestBody == null ? null : GSON.toJson(requestBody).getBytes(StandardCharsets.UTF_8);
        if (encoded != null && encoded.length > MAX_REQUEST_BYTES) throw new IOException("Server-directory request is too large");
        URL url = new URL(config.apiBaseUrl + path);
        if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IOException("Server-directory API must use HTTPS");
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Accept-Encoding", "gzip");
        connection.setRequestProperty("User-Agent", "UberBukkit-MCOSE-ServerDirectory/2");
        if (encoded != null) {
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(encoded.length);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            OutputStream output = connection.getOutputStream();
            try {
                output.write(encoded);
            } finally {
                output.close();
            }
        }

        int status = connection.getResponseCode();
        InputStream raw = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        byte[] responseBytes;
        try {
            InputStream input = raw;
            if (input != null && "gzip".equalsIgnoreCase(connection.getContentEncoding())) input = new GZIPInputStream(input);
            responseBytes = readBounded(input, MAX_RESPONSE_BYTES);
        } finally {
            if (raw != null) raw.close();
            connection.disconnect();
        }
        JsonObject response = parseObject(responseBytes);
        if (status < 200 || status >= 300) {
            JsonObject error = response.has("error") && response.get("error").isJsonObject() ? response.getAsJsonObject("error") : new JsonObject();
            String code = string(error, "code", "website_error");
            String message = string(error, "message", "The directory website rejected the request.");
            ServerDirectoryProtocol.Listing listing = null;
            if (response.has("listing") && response.get("listing").isJsonObject()) listing = parseListing(response.getAsJsonObject("listing"));
            throw new WebsiteException(status, code, message, listing);
        }
        return response;
    }

    static ServerDirectoryProtocol.Listing parseListing(JsonObject object) throws IOException {
        if (object == null) throw new IOException("Directory response is missing listing data");
        try {
            String listingId = string(object, "listingId", "");
            long listingNumber = object.get("listingNumber").getAsLong();
            int revision = object.get("revision").getAsInt();
            String name = string(object, "name", "");
            String description = string(object, "description", "");
            JsonArray array = object.getAsJsonArray("tagIds");
            if (listingId.length() > 36 || listingNumber < 1 || revision < 1 || name.length() < 1 || name.length() > 64
                    || description.length() < 1 || description.length() > 200 || array == null || array.size() < 1 || array.size() > 3) {
                throw new IOException("Directory website returned an invalid listing");
            }
            List<String> tags = new ArrayList<String>(array.size());
            for (JsonElement element : array) {
                String tag = element.getAsString();
                if (!tag.matches("[a-z0-9_-]{1,32}") || tags.contains(tag)) throw new IOException("Directory website returned invalid tags");
                tags.add(tag);
            }
            return new ServerDirectoryProtocol.Listing(listingId, listingNumber, revision, name, description, tags);
        } catch (RuntimeException invalid) {
            throw new IOException("Directory website returned malformed listing JSON", invalid);
        }
    }

    private static JsonObject parseObject(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) return new JsonObject();
        try {
            JsonElement parsed = new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new IOException("Directory website returned non-object JSON");
            return parsed.getAsJsonObject();
        } catch (RuntimeException invalid) {
            throw new IOException("Directory website returned malformed JSON", invalid);
        }
    }

    private static byte[] readBounded(InputStream input, int maximum) throws IOException {
        if (input == null) return new byte[0];
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > maximum) throw new IOException("Directory website response is too large");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String string(JsonObject object, String name, String fallback) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }

    public static final class WebsiteException extends Exception {
        public final int status;
        public final String code;
        public final ServerDirectoryProtocol.Listing listing;

        WebsiteException(int status, String code, String message, ServerDirectoryProtocol.Listing listing) {
            super(message);
            this.status = status;
            this.code = code;
            this.listing = listing;
        }
    }
}
