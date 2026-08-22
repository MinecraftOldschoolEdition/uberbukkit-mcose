package net.minecraft.server.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bounded payload codec for the server-rules GUI.  This remains separate from
 * the handshake codec so malformed editor input can never affect negotiation.
 */
public final class ServerRulesProtocol {
    public static final String CHANNEL_SHOW = "MCOSE|RULES_SHOW";
    public static final String CHANNEL_ACTION = "MCOSE|RULES_ACTION";

    public static final int VERSION = 1;
    public static final int SCREEN_VIEW = 0;
    public static final int SCREEN_CONSENT = 1;
    public static final int SCREEN_EDIT = 2;

    public static final int ACTION_AGREE = 1;
    public static final int ACTION_DISCONNECT = 2;
    public static final int ACTION_SAVE = 3;

    public static final int MAX_RULES = 64;
    public static final int MAX_RULE_CHARS = 256;
    public static final int MAX_TOTAL_RULE_CHARS = 12288;

    private ServerRulesProtocol() {
    }

    public static byte[] createShowPayload(int screen, List<String> rules) {
        if (!isScreen(screen)) {
            return new byte[0];
        }
        List<String> normalized = normalizeRules(rules, false);
        if (normalized == null) {
            return new byte[0];
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeByte(VERSION);
            output.writeByte(screen);
            output.writeShort(normalized.size());
            for (int i = 0; i < normalized.size(); ++i) {
                output.writeUTF((String) normalized.get(i));
            }
            output.flush();
            return bytes.toByteArray();
        } catch (Throwable ignored) {
            return new byte[0];
        }
    }

    public static ScreenData readShowPayload(byte[] payload) {
        if (payload == null || payload.length < 4) {
            return null;
        }
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
            int version = input.readUnsignedByte();
            int screen = input.readUnsignedByte();
            int count = input.readUnsignedShort();
            if (version != VERSION || !isScreen(screen) || count > MAX_RULES) {
                input.close();
                return null;
            }
            ArrayList<String> rules = new ArrayList<String>(count);
            int total = 0;
            for (int i = 0; i < count; ++i) {
                String rule = input.readUTF();
                if (!isValidRule(rule)) {
                    input.close();
                    return null;
                }
                total += rule.length();
                if (total > MAX_TOTAL_RULE_CHARS) {
                    input.close();
                    return null;
                }
                rules.add(rule);
            }
            boolean complete = input.available() == 0;
            input.close();
            return complete ? new ScreenData(screen, rules) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static byte[] createActionPayload(int action, List<String> rules) {
        if (action != ACTION_AGREE && action != ACTION_DISCONNECT && action != ACTION_SAVE) {
            return new byte[0];
        }
        List<String> normalized = action == ACTION_SAVE ? normalizeRules(rules, true) : Collections.<String>emptyList();
        if (normalized == null) {
            return new byte[0];
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeByte(VERSION);
            output.writeByte(action);
            if (action == ACTION_SAVE) {
                output.writeShort(normalized.size());
                for (int i = 0; i < normalized.size(); ++i) {
                    output.writeUTF((String) normalized.get(i));
                }
            }
            output.flush();
            return bytes.toByteArray();
        } catch (Throwable ignored) {
            return new byte[0];
        }
    }

    public static ActionData readActionPayload(byte[] payload) {
        if (payload == null || payload.length < 2) {
            return null;
        }
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
            int version = input.readUnsignedByte();
            int action = input.readUnsignedByte();
            if (version != VERSION || (action != ACTION_AGREE && action != ACTION_DISCONNECT && action != ACTION_SAVE)) {
                input.close();
                return null;
            }
            ArrayList<String> rules = new ArrayList<String>();
            if (action == ACTION_SAVE) {
                int count = input.readUnsignedShort();
                if (count > MAX_RULES) {
                    input.close();
                    return null;
                }
                int total = 0;
                for (int i = 0; i < count; ++i) {
                    String rule = input.readUTF();
                    if (!isValidRule(rule)) {
                        input.close();
                        return null;
                    }
                    total += rule.length();
                    if (total > MAX_TOTAL_RULE_CHARS) {
                        input.close();
                        return null;
                    }
                    rules.add(rule);
                }
            }
            boolean complete = input.available() == 0;
            input.close();
            return complete ? new ActionData(action, rules) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static List<String> normalizeRules(List<String> rules, boolean allowEmpty) {
        if (rules == null || rules.size() > MAX_RULES) {
            return null;
        }
        ArrayList<String> result = new ArrayList<String>(rules.size());
        int total = 0;
        for (int i = 0; i < rules.size(); ++i) {
            String rule = (String) rules.get(i);
            if (rule == null) {
                return null;
            }
            rule = rule.trim();
            if (rule.length() == 0) {
                continue;
            }
            if (!isValidRule(rule)) {
                return null;
            }
            total += rule.length();
            if (total > MAX_TOTAL_RULE_CHARS) {
                return null;
            }
            result.add(rule);
        }
        if (!allowEmpty && result.isEmpty()) {
            return Collections.singletonList("No server rules have been configured.");
        }
        return Collections.unmodifiableList(result);
    }

    private static boolean isScreen(int screen) {
        return screen == SCREEN_VIEW || screen == SCREEN_CONSENT || screen == SCREEN_EDIT;
    }

    private static boolean isValidRule(String rule) {
        if (rule == null || rule.length() == 0 || rule.length() > MAX_RULE_CHARS) {
            return false;
        }
        for (int i = 0; i < rule.length(); ++i) {
            char c = rule.charAt(i);
            if (c < 32 || c == 127) {
                return false;
            }
        }
        return true;
    }

    public static final class ScreenData {
        public final int screen;
        public final List<String> rules;

        public ScreenData(int screen, List<String> rules) {
            this.screen = screen;
            this.rules = Collections.unmodifiableList(new ArrayList<String>(rules));
        }
    }

    public static final class ActionData {
        public final int action;
        public final List<String> rules;

        public ActionData(int action, List<String> rules) {
            this.action = action;
            this.rules = Collections.unmodifiableList(new ArrayList<String>(rules));
        }
    }
}
