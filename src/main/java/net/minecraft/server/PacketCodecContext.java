package net.minecraft.server;

/** Per-connection wire policy installed by the network reader and writer threads. */
public final class PacketCodecContext {
    private static final ThreadLocal<Boolean> ITEM_COMPONENTS_OVERRIDE = new ThreadLocal<Boolean>();

    private PacketCodecContext() {}

    public static Scope overrideItemComponents(Boolean enabled) {
        Boolean previous = ITEM_COMPONENTS_OVERRIDE.get();
        if (enabled == null) {
            ITEM_COMPONENTS_OVERRIDE.remove();
        } else {
            ITEM_COMPONENTS_OVERRIDE.set(enabled);
        }
        return new Scope(previous);
    }

    /** True only after this physical stream direction crossed the envelope-v1 handshake barrier. */
    public static boolean usesItemComponents() {
        Boolean override = ITEM_COMPONENTS_OVERRIDE.get();
        return override != null && override.booleanValue();
    }

    public static final class Scope implements AutoCloseable {
        private final Boolean previous;
        private boolean closed;

        private Scope(Boolean previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            if (this.previous == null) {
                ITEM_COMPONENTS_OVERRIDE.remove();
            } else {
                ITEM_COMPONENTS_OVERRIDE.set(this.previous);
            }
        }
    }
}
