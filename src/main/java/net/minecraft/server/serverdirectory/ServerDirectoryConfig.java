package net.minecraft.server.serverdirectory;

import com.google.common.net.InetAddresses;
import uk.betacraft.uberbukkit.UberbukkitConfig;

import java.net.IDN;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

/** Immutable validated publication configuration. No DNS lookup is performed. */
public final class ServerDirectoryConfig {
    public final boolean enabled;
    public final String apiBaseUrl;
    public final String host;
    public final int port;
    public final String publicEndpoint;
    public final String configurationError;

    private ServerDirectoryConfig(boolean enabled, String apiBaseUrl, String host, int port,
                                  String publicEndpoint, String configurationError) {
        this.enabled = enabled;
        this.apiBaseUrl = apiBaseUrl;
        this.host = host;
        this.port = port;
        this.publicEndpoint = publicEndpoint;
        this.configurationError = configurationError;
    }

    public static ServerDirectoryConfig load() {
        UberbukkitConfig config = UberbukkitConfig.getInstance();
        boolean enabled = config.getBoolean("server-browser.enabled", true);
        String apiBase = trim(config.getString("server-browser.api-base-url", "https://minecraftoldschool.com"));
        String address = trim(config.getString("server-browser.public-address", ""));

        return fromValues(enabled, apiBase, address);
    }

    static ServerDirectoryConfig fromValues(boolean enabled, String apiBaseValue, String addressValue) {
        String apiBase = trim(apiBaseValue);
        String address = trim(addressValue);

        String error = null;
        String host = "";
        int port = 25565;
        String endpoint = "";
        if (!enabled) {
            error = "Server directory publication is disabled in uberbukkit.yml.";
        } else {
            try {
                URL url = new URL(apiBase);
                if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getHost().length() == 0 || url.getUserInfo() != null || url.getQuery() != null || url.getRef() != null) {
                    throw new MalformedURLException("HTTPS base URL required");
                }
                apiBase = apiBase.replaceAll("/+$", "");
            } catch (Exception invalidUrl) {
                error = "server-browser.api-base-url must be a valid HTTPS URL.";
            }
            if (error == null) {
                try {
                    Endpoint parsed = parseEndpoint(address);
                    host = parsed.host;
                    port = parsed.port;
                    endpoint = parsed.formatted;
                } catch (IllegalArgumentException invalidAddress) {
                    error = invalidAddress.getMessage();
                }
            }
        }
        return new ServerDirectoryConfig(enabled, apiBase, host, port, endpoint, error);
    }

    public boolean isReady() {
        return enabled && configurationError == null;
    }

    static Endpoint parseEndpoint(String value) {
        String input = trim(value);
        if (input.length() == 0) throw new IllegalArgumentException("server-browser.public-address is required.");
        if (input.contains("://") || input.indexOf('/') >= 0 || input.indexOf('\\') >= 0 || input.indexOf('@') >= 0
                || input.indexOf('?') >= 0 || input.indexOf('#') >= 0) {
            throw new IllegalArgumentException("server-browser.public-address must contain only a hostname and optional port.");
        }
        String rawHost;
        String rawPort = null;
        if (input.charAt(0) == '[') {
            int close = input.indexOf(']');
            if (close <= 1) throw new IllegalArgumentException("Invalid bracketed IPv6 public address.");
            rawHost = input.substring(1, close);
            if (close + 1 < input.length()) {
                if (input.charAt(close + 1) != ':') throw new IllegalArgumentException("Invalid public address suffix.");
                rawPort = input.substring(close + 2);
            }
        } else {
            int firstColon = input.indexOf(':');
            int lastColon = input.lastIndexOf(':');
            if (firstColon >= 0 && firstColon != lastColon) {
                rawHost = input;
            } else if (lastColon > 0) {
                rawHost = input.substring(0, lastColon);
                rawPort = input.substring(lastColon + 1);
            } else {
                rawHost = input;
            }
        }
        int port = 25565;
        if (rawPort != null) {
            try {
                port = Integer.parseInt(rawPort);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Public server port must be an integer from 1 to 65535.");
            }
        }
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Public server port must be an integer from 1 to 65535.");
        String host = normalizeHost(rawHost);
        String formatted = host.indexOf(':') >= 0 ? "[" + host + "]:" + port : host + ":" + port;
        return new Endpoint(host, port, formatted);
    }

    static String normalizeHost(String value) {
        String raw = trim(value).toLowerCase(Locale.ROOT);
        if (raw.length() == 0 || raw.length() > 253) throw new IllegalArgumentException("Public server hostname is invalid.");
        boolean literal = raw.indexOf(':') >= 0 || raw.matches("[0-9.]+");
        if (literal) {
            try {
                InetAddress address = InetAddresses.forString(raw);
                if (!isGlobalUnicast(address.getAddress())) throw new IllegalArgumentException("Public server address must be global unicast.");
                return InetAddresses.toAddrString(address).toLowerCase(Locale.ROOT);
            } catch (IllegalArgumentException invalid) {
                if (invalid.getMessage() != null && invalid.getMessage().contains("global unicast")) throw invalid;
                throw new IllegalArgumentException("Public server IP literal is invalid.");
            }
        }
        final String ascii;
        try {
            ascii = IDN.toASCII(raw, IDN.USE_STD3_ASCII_RULES).replaceAll("\\.$", "").toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Public server hostname is invalid.");
        }
        String[] labels = ascii.split("\\.");
        if (labels.length < 2) throw new IllegalArgumentException("Public server hostname must be fully qualified.");
        for (String label : labels) {
            if (!label.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) throw new IllegalArgumentException("Public server hostname is invalid.");
        }
        String suffix = labels[labels.length - 1];
        if ("local".equals(suffix) || "localhost".equals(suffix) || "internal".equals(suffix)
                || "home".equals(suffix) || "test".equals(suffix) || "invalid".equals(suffix) || "example".equals(suffix)) {
            throw new IllegalArgumentException("Public server hostname must not be local or reserved.");
        }
        return ascii;
    }

    static boolean isGlobalUnicast(byte[] address) {
        if (address.length == 4) {
            int a = address[0] & 255;
            int b = address[1] & 255;
            int c = address[2] & 255;
            if (a == 0 || a == 10 || a == 127 || a >= 224) return false;
            if (a == 100 && b >= 64 && b <= 127) return false;
            if (a == 169 && b == 254) return false;
            if (a == 172 && b >= 16 && b <= 31) return false;
            if (a == 192 && b == 168) return false;
            if (a == 192 && b == 0 && (c == 0 || c == 2)) return false;
            if (a == 198 && (b == 18 || b == 19)) return false;
            if (a == 198 && b == 51 && c == 100) return false;
            if (a == 203 && b == 0 && c == 113) return false;
            return true;
        }
        if (address.length != 16) return false;
        int first = address[0] & 255;
        int second = address[1] & 255;
        boolean allZero = true;
        for (byte b : address) if (b != 0) allZero = false;
        if (allZero || isIpv6Loopback(address) || first == 0xff || (first & 0xfe) == 0xfc || first == 0xfe && (second & 0xc0) == 0x80) return false;
        if (first == 0x20 && second == 0x01 && (address[2] & 255) == 0x0d && (address[3] & 255) == 0xb8) return false;
        return (first & 0xe0) == 0x20;
    }

    private static boolean isIpv6Loopback(byte[] address) {
        for (int i = 0; i < 15; ++i) if (address[i] != 0) return false;
        return address[15] == 1;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    static final class Endpoint {
        final String host;
        final int port;
        final String formatted;

        Endpoint(String host, int port, String formatted) {
            this.host = host;
            this.port = port;
            this.formatted = formatted;
        }
    }
}
