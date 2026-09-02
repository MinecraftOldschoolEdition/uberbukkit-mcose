package net.minecraft.server.registry;

import java.util.List;
import net.minecraft.server.Item;
import net.minecraft.server.util.ResourceLocation;

/** Immutable published item-tag generation used by gameplay. */
public final class ItemTagRegistryApi {
    private static volatile State state = State.empty();

    private ItemTagRegistryApi() {}

    static void publishBootstrap(
            final RegistryTagBindings<Item> bindings,
            long expectedItemRegistryRevision) {
        if (bindings == null) {
            throw new IllegalArgumentException(
                    "Item-tag bootstrap candidate cannot be null");
        }
        if (!ItemTags.REGISTRY.equals(bindings.registryKey())
                || bindings.registryRevision()
                        != expectedItemRegistryRevision) {
            throw new IllegalStateException(
                    "Item registry changed while item tags were being prepared");
        }

        final State candidate = new State(bindings);
        boolean published = ItemRegistry.publishIfRevision(
                expectedItemRegistryRevision,
                new Runnable() {
                    public void run() {
                        synchronized (ItemTagRegistryApi.class) {
                            state = candidate;
                        }
                    }
                });
        if (!published) {
            throw new IllegalStateException(
                    "Item registry changed before item-tag publication");
        }
    }

    public static RegistryTagBindings<Item> tagBindings() {
        return state.bindings;
    }

    public static boolean areTagBindingsCurrent() {
        return state.bindings.isForRevision(
                ItemRegistry.trackedRegistrationRevision());
    }

    public static boolean isInTag(TagKey<Item> tag, Item item) {
        synchronized (ItemRegistry.class) {
            return currentState().bindings.contains(tag, item);
        }
    }

    public static List<ResourceLocation> tagMemberKeys(TagKey<Item> tag) {
        synchronized (ItemRegistry.class) {
            return currentState().bindings.valueKeys(tag);
        }
    }

    public static List<TagKey<Item>> synchronizedTagKeys() {
        return ItemTags.synchronizedTags();
    }

    private static State currentState() {
        State current = state;
        long registryRevision = ItemRegistry.trackedRegistrationRevision();
        if (!current.bindings.isForRevision(registryRevision)) {
            throw new IllegalStateException(
                    "Item tag bindings are stale for registry revision "
                            + registryRevision + " (bound "
                            + current.bindings.registryRevision() + ")");
        }
        return current;
    }

    private static final class State {
        final RegistryTagBindings<Item> bindings;

        State(RegistryTagBindings<Item> bindings) {
            this.bindings = bindings;
        }

        static State empty() {
            return new State(RegistryTagBindings.<Item>empty(
                    ItemTags.REGISTRY, -1L));
        }
    }
}
