package net.minecraft.server;

/** Translation key plus immutable arguments selected by a death-message rule. */
public final class DeathMessage {
    private final String translationKey;
    private final Object[] arguments;

    public DeathMessage(String translationKey, Object... arguments) {
        if (translationKey == null || translationKey.trim().length() == 0) {
            throw new IllegalArgumentException(
                    "Missing death-message translation key");
        }
        this.translationKey = translationKey;
        this.arguments = arguments == null
                ? new Object[0] : arguments.clone();
    }

    public String getTranslationKey() {
        return this.translationKey;
    }

    public Object[] getArguments() {
        return this.arguments.clone();
    }

    public String format() {
        return StatisticCollector.a(this.translationKey, this.arguments);
    }
}
