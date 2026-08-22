package net.minecraft.server.serverdirectory;

import net.minecraft.server.PacketLimits;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Binary MCOSE server-directory capability protocol, version 1. */
public final class ServerDirectoryProtocol {
    public static final int VERSION = 1;
    public static final String CHANNEL_REQUEST = "MCOSE|SDIR_REQ";
    public static final String CHANNEL_STATE = "MCOSE|SDIR_STATE";
    public static final String CHANNEL_MUTATION = "MCOSE|SDIR_MUT";
    public static final String CHANNEL_RESULT = "MCOSE|SDIR_RESULT";

    public static final int OP_UPSERT = 1;
    public static final int OP_DELETE = 2;

    public static final int RESULT_SUCCESS = 0;
    public static final int RESULT_VALIDATION = 1;
    public static final int RESULT_CONFLICT = 2;
    public static final int RESULT_AUTHORIZATION = 3;
    public static final int RESULT_CONFIGURATION = 4;
    public static final int RESULT_WEBSITE = 5;

    public static final int MAX_REQUEST_BYTES = 4;
    public static final int MAX_MUTATION_BYTES = 1024;
    public static final int MAX_NAME_CHARS = 64;
    public static final int MAX_DESCRIPTION_CHARS = 200;
    public static final int MAX_ENDPOINT_CHARS = 320;
    public static final int MAX_TAG_ID_CHARS = 32;
    public static final int MAX_TAGS = 3;
    public static final int MAX_MESSAGE_CHARS = 300;

    private ServerDirectoryProtocol() {}

    public static byte[] createRequestPayload() {
        return new byte[] {(byte) VERSION};
    }

    public static void validateRequestPayload(byte[] payload) throws IOException {
        if (payload == null || payload.length != 1 || (payload[0] & 255) != VERSION) {
            throw new IOException("Invalid server-directory capability request");
        }
    }

    public static Mutation readMutationPayload(byte[] payload) throws IOException {
        if (payload == null || payload.length < 6 || payload.length > MAX_MUTATION_BYTES) {
            throw new IOException("Invalid server-directory mutation payload size");
        }
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        try {
            int version = in.readUnsignedByte();
            if (version != VERSION) throw new IOException("Unsupported server-directory mutation version");
            int operation = in.readUnsignedByte();
            int expectedRevision = in.readInt();
            if (expectedRevision < 0) throw new IOException("Invalid expected revision");
            if (operation == OP_DELETE) {
                if (expectedRevision < 1 || in.available() != 0) throw new IOException("Invalid delete payload");
                return Mutation.delete(expectedRevision);
            }
            if (operation != OP_UPSERT) throw new IOException("Unknown server-directory mutation operation");
            String name = normalizeVisible(PacketLimits.readUtf(in, MAX_NAME_CHARS, "server name"), 1, MAX_NAME_CHARS, "server name");
            String description = normalizeVisible(PacketLimits.readUtf(in, MAX_DESCRIPTION_CHARS, "description"), 1, MAX_DESCRIPTION_CHARS, "description");
            int tagCount = in.readUnsignedByte();
            if (tagCount < 1 || tagCount > MAX_TAGS) throw new IOException("A listing must contain one to three tags");
            List<String> tags = new ArrayList<String>(tagCount);
            for (int i = 0; i < tagCount; ++i) {
                String tag = PacketLimits.readUtf(in, MAX_TAG_ID_CHARS, "tag ID").trim().toLowerCase(java.util.Locale.ROOT);
                if (!tag.matches("[a-z0-9_-]{1,32}") || tags.contains(tag)) throw new IOException("Invalid or duplicate tag ID");
                tags.add(tag);
            }
            if (in.available() != 0) throw new IOException("Trailing bytes in server-directory mutation");
            return Mutation.upsert(expectedRevision, name, description, tags);
        } finally {
            in.close();
        }
    }

    public static byte[] createMutationPayload(Mutation mutation) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(VERSION);
        out.writeByte(mutation.operation);
        out.writeInt(mutation.expectedRevision);
        if (mutation.operation == OP_UPSERT) {
            PacketLimits.writeUtf(out, mutation.name, MAX_NAME_CHARS, "server name");
            PacketLimits.writeUtf(out, mutation.description, MAX_DESCRIPTION_CHARS, "description");
            out.writeByte(mutation.tagIds.size());
            for (String tag : mutation.tagIds) PacketLimits.writeUtf(out, tag, MAX_TAG_ID_CHARS, "tag ID");
        }
        out.flush();
        byte[] payload = bytes.toByteArray();
        if (payload.length > MAX_MUTATION_BYTES) throw new IOException("Server-directory mutation is too large");
        return payload;
    }

    public static byte[] createStatePayload(State state) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(VERSION);
        out.writeBoolean(state.supported);
        out.writeBoolean(state.permitted);
        out.writeBoolean(state.ready);
        out.writeBoolean(state.loading);
        PacketLimits.writeUtf(out, state.publicEndpoint, MAX_ENDPOINT_CHARS, "public endpoint");
        PacketLimits.writeUtf(out, state.creatorUsername, PacketLimits.MAX_USERNAME_CHARS, "creator username");
        PacketLimits.writeUtf(out, state.message, MAX_MESSAGE_CHARS, "state message");
        out.writeBoolean(state.listing != null);
        if (state.listing != null) writeListing(out, state.listing);
        out.flush();
        return bytes.toByteArray();
    }

    public static State readStatePayload(byte[] payload) throws IOException {
        if (payload == null || payload.length == 0 || payload.length > PacketLimits.MAX_CUSTOM_PAYLOAD_BYTES) throw new IOException("Invalid state payload size");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        try {
            if (in.readUnsignedByte() != VERSION) throw new IOException("Unsupported server-directory state version");
            boolean supported = in.readBoolean();
            boolean permitted = in.readBoolean();
            boolean ready = in.readBoolean();
            boolean loading = in.readBoolean();
            String endpoint = PacketLimits.readUtf(in, MAX_ENDPOINT_CHARS, "public endpoint");
            String creator = PacketLimits.readUtf(in, PacketLimits.MAX_USERNAME_CHARS, "creator username");
            String message = PacketLimits.readUtf(in, MAX_MESSAGE_CHARS, "state message");
            Listing listing = in.readBoolean() ? readListing(in) : null;
            if (in.available() != 0) throw new IOException("Trailing bytes in server-directory state");
            return new State(supported, permitted, ready, loading, endpoint, creator, message, listing);
        } finally {
            in.close();
        }
    }

    public static byte[] createResultPayload(int resultCode, String message) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(VERSION);
        out.writeByte(resultCode);
        PacketLimits.writeUtf(out, normalizeMessage(message), MAX_MESSAGE_CHARS, "result message");
        out.flush();
        return bytes.toByteArray();
    }

    public static Result readResultPayload(byte[] payload) throws IOException {
        if (payload == null || payload.length < 4 || payload.length > 1024) throw new IOException("Invalid result payload size");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        try {
            if (in.readUnsignedByte() != VERSION) throw new IOException("Unsupported result version");
            int code = in.readUnsignedByte();
            if (code < RESULT_SUCCESS || code > RESULT_WEBSITE) throw new IOException("Unknown result code");
            String message = PacketLimits.readUtf(in, MAX_MESSAGE_CHARS, "result message");
            if (in.available() != 0) throw new IOException("Trailing bytes in result payload");
            return new Result(code, message);
        } finally {
            in.close();
        }
    }

    private static void writeListing(DataOutputStream out, Listing listing) throws IOException {
        PacketLimits.writeUtf(out, listing.listingId, PacketLimits.MAX_UUID_CHARS, "listing ID");
        out.writeLong(listing.listingNumber);
        out.writeInt(listing.revision);
        PacketLimits.writeUtf(out, listing.name, MAX_NAME_CHARS, "server name");
        PacketLimits.writeUtf(out, listing.description, MAX_DESCRIPTION_CHARS, "description");
        if (listing.tagIds.size() < 1 || listing.tagIds.size() > MAX_TAGS) throw new IOException("Invalid listing tag count");
        out.writeByte(listing.tagIds.size());
        for (String tag : listing.tagIds) PacketLimits.writeUtf(out, tag, MAX_TAG_ID_CHARS, "tag ID");
    }

    private static Listing readListing(DataInputStream in) throws IOException {
        String listingId = PacketLimits.readUtf(in, PacketLimits.MAX_UUID_CHARS, "listing ID");
        long listingNumber = in.readLong();
        int revision = in.readInt();
        String name = PacketLimits.readUtf(in, MAX_NAME_CHARS, "server name");
        String description = PacketLimits.readUtf(in, MAX_DESCRIPTION_CHARS, "description");
        int count = in.readUnsignedByte();
        if (count < 1 || count > MAX_TAGS) throw new IOException("Invalid listing tag count");
        List<String> tags = new ArrayList<String>(count);
        for (int i = 0; i < count; ++i) tags.add(PacketLimits.readUtf(in, MAX_TAG_ID_CHARS, "tag ID"));
        return new Listing(listingId, listingNumber, revision, name, description, tags);
    }

    private static String normalizeVisible(String value, int minimum, int maximum, String field) throws IOException {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (normalized.length() < minimum || normalized.length() > maximum) throw new IOException(field + " has an invalid length");
        for (int i = 0; i < normalized.length(); ++i) {
            char c = normalized.charAt(i);
            if (c < 32 || c == 127 || c == '\u00a7' || c == '<' || c == '>') throw new IOException(field + " contains forbidden characters");
        }
        return normalized;
    }

    private static String normalizeMessage(String message) {
        if (message == null) return "";
        String clean = message.replaceAll("[\\u0000-\\u001f\\u007f]", " ").replaceAll("\\s+", " ").trim();
        return clean.length() <= MAX_MESSAGE_CHARS ? clean : clean.substring(0, MAX_MESSAGE_CHARS);
    }

    public static final class Mutation {
        public final int operation;
        public final int expectedRevision;
        public final String name;
        public final String description;
        public final List<String> tagIds;

        private Mutation(int operation, int expectedRevision, String name, String description, List<String> tagIds) {
            this.operation = operation;
            this.expectedRevision = expectedRevision;
            this.name = name;
            this.description = description;
            this.tagIds = Collections.unmodifiableList(new ArrayList<String>(tagIds));
        }

        public static Mutation upsert(int expectedRevision, String name, String description, List<String> tags) {
            return new Mutation(OP_UPSERT, expectedRevision, name, description, tags);
        }

        public static Mutation delete(int expectedRevision) {
            return new Mutation(OP_DELETE, expectedRevision, "", "", Collections.<String>emptyList());
        }
    }

    public static final class Listing {
        public final String listingId;
        public final long listingNumber;
        public final int revision;
        public final String name;
        public final String description;
        public final List<String> tagIds;

        public Listing(String listingId, long listingNumber, int revision, String name, String description, List<String> tagIds) {
            this.listingId = listingId;
            this.listingNumber = listingNumber;
            this.revision = revision;
            this.name = name;
            this.description = description;
            this.tagIds = Collections.unmodifiableList(new ArrayList<String>(tagIds));
        }
    }

    public static final class State {
        public final boolean supported;
        public final boolean permitted;
        public final boolean ready;
        public final boolean loading;
        public final String publicEndpoint;
        public final String creatorUsername;
        public final String message;
        public final Listing listing;

        public State(boolean supported, boolean permitted, boolean ready, boolean loading, String publicEndpoint, String creatorUsername, String message, Listing listing) {
            this.supported = supported;
            this.permitted = permitted;
            this.ready = ready;
            this.loading = loading;
            this.publicEndpoint = publicEndpoint == null ? "" : publicEndpoint;
            this.creatorUsername = creatorUsername == null ? "" : creatorUsername;
            this.message = message == null ? "" : message;
            this.listing = listing;
        }
    }

    public static final class Result {
        public final int code;
        public final String message;

        public Result(int code, String message) {
            this.code = code;
            this.message = message;
        }
    }
}
