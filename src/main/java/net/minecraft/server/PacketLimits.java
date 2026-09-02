package net.minecraft.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class PacketLimits {

    public static final int MAX_CUSTOM_PAYLOAD_BYTES = 32767;
    public static final int MAX_CUSTOM_CHANNEL_CHARS = 64;
    public static final int MAX_HANDSHAKE_CHARS = 255;
    public static final int MAX_ITEM_NBT_BYTES = 32767;
    public static final int MAX_TAB_COMPLETIONS = 1000;
    public static final int MAX_COMMAND_TEXT_CHARS = 1024;
    public static final int MAX_COMPLETION_CHARS = 256;
    public static final int MAX_BOOK_PAGES = 100;
    public static final int MAX_BOOK_PAGE_CHARS = 1024;
    public static final int MAX_BOOK_TITLE_CHARS = 32;
    public static final int MAX_NAME_TAG_CHARS = 64;
    public static final int MAX_VERSION_CHARS = 64;
    public static final int MAX_USERNAME_CHARS = 32;
    public static final int MAX_UUID_CHARS = 36;
    // Ed25519 uses a 64-byte signature and 32-byte public key. Their canonical
    // padded Base64 encodings are exactly 88 and 44 characters respectively.
    public static final int MAX_SIGNATURE_CHARS = 88;
    public static final int MAX_PUBLIC_KEY_CHARS = 44;
    public static final int MAX_AUTH_BYTE_ARRAY_BYTES = 512;

    private PacketLimits() {}

    public static String readUtf(DataInputStream input, int maxChars, String fieldName) throws IOException {
        String value = input.readUTF();
        if (value.length() > maxChars) {
            throw new IOException(fieldName + " is too long (" + value.length() + " > " + maxChars + ")");
        }
        return value;
    }

    public static void writeUtf(DataOutputStream output, String value, int maxChars, String fieldName) throws IOException {
        if (value == null) {
            value = "";
        }
        if (value.length() > maxChars) {
            throw new IOException(fieldName + " is too long (" + value.length() + " > " + maxChars + ")");
        }
        output.writeUTF(value);
    }

    public static byte[] readUnsignedShortByteArray(DataInputStream input, int maxBytes, String fieldName) throws IOException {
        int length = input.readUnsignedShort();
        if (length > maxBytes) {
            throw new IOException(fieldName + " payload is too large (" + length + " > " + maxBytes + ")");
        }

        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return bytes;
    }

    public static byte[] readNullableShortByteArray(DataInputStream input, int maxBytes, String fieldName) throws IOException {
        short rawLength = input.readShort();
        if (rawLength == -1) {
            return null;
        }
        if (rawLength < -1) {
            throw new IOException("Invalid " + fieldName + " length: " + rawLength);
        }

        int length = rawLength;
        if (length > maxBytes) {
            throw new IOException(fieldName + " payload is too large (" + length + " > " + maxBytes + ")");
        }

        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return bytes;
    }

    public static void writeUnsignedShortByteArray(DataOutputStream output, byte[] bytes, int maxBytes, String fieldName) throws IOException {
        if (bytes == null) {
            bytes = new byte[0];
        }
        if (bytes.length > maxBytes) {
            throw new IOException(fieldName + " payload is too large (" + bytes.length + " > " + maxBytes + ")");
        }

        output.writeShort(bytes.length);
        output.write(bytes);
    }

    public static NBTTagCompound readCompressedNBT(DataInputStream input, int maxCompressedBytes, String fieldName) throws IOException {
        return readCompressedNBT(input, maxCompressedBytes, fieldName, NBTReadLimiter.packet());
    }

    public static NBTTagCompound readCompressedNBT(
            DataInputStream input,
            int maxCompressedBytes,
            String fieldName,
            NBTReadLimiter limiter) throws IOException {
        byte[] nbtBytes = readNullableShortByteArray(input, maxCompressedBytes, fieldName);
        if (nbtBytes == null || nbtBytes.length == 0) {
            return null;
        }

        DataInputStream nbtInput = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(nbtBytes)));
        try {
            NBTBase nbtBase = NBTBase.b(nbtInput, limiter == null ? NBTReadLimiter.packet() : limiter);
            return nbtBase instanceof NBTTagCompound ? (NBTTagCompound) nbtBase : null;
        } finally {
            nbtInput.close();
        }
    }

    public static void writeCompressedNBT(DataOutputStream output, NBTTagCompound tag, int maxCompressedBytes, String fieldName) throws IOException {
        if (tag == null) {
            output.writeShort(-1);
            return;
        }

        ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
        DataOutputStream nbtOutput = new DataOutputStream(new GZIPOutputStream(byteOutput));
        try {
            NBTBase.a(tag, nbtOutput);
        } finally {
            nbtOutput.close();
        }

        byte[] nbtBytes = byteOutput.toByteArray();
        if (nbtBytes.length > maxCompressedBytes) {
            throw new IOException(fieldName + " payload is too large (" + nbtBytes.length + " > " + maxCompressedBytes + ")");
        }

        output.writeShort(nbtBytes.length);
        output.write(nbtBytes);
    }
}
