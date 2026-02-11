package net.minecraft.server.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Synchronous in-process event bus.
 */
public final class EventBus {
    private static final EventBus GLOBAL = new EventBus();

    private static final Comparator<Subscription> ORDER = new Comparator<Subscription>() {
        public int compare(Subscription a, Subscription b) {
            return a.priority.ordinal() - b.priority.ordinal();
        }
    };

    private final Map<Class<?>, List<Subscription>> subscribers = new HashMap<Class<?>, List<Subscription>>();

    public static EventBus global() {
        return GLOBAL;
    }

    public synchronized <E> void subscribe(Class<E> type, EventListener<E> listener) {
        subscribe(type, EventPriority.NORMAL, listener);
    }

    public synchronized <E> void subscribe(Class<E> type, EventPriority priority, EventListener<E> listener) {
        if (type == null || listener == null) {
            return;
        }

        List<Subscription> list = subscribers.get(type);
        if (list == null) {
            list = new ArrayList<Subscription>();
            subscribers.put(type, list);
        }
        list.add(new Subscription(priority == null ? EventPriority.NORMAL : priority, listener));
        Collections.sort(list, ORDER);
    }

    public synchronized <E> void unsubscribe(Class<E> type, EventListener<E> listener) {
        if (type == null || listener == null) {
            return;
        }

        List<Subscription> list = subscribers.get(type);
        if (list == null) {
            return;
        }

        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).listener == listener) {
                list.remove(i);
            }
        }

        if (list.isEmpty()) {
            subscribers.remove(type);
        }
    }

    public void publish(Object event) {
        if (event == null) {
            return;
        }

        List<Subscription> list;
        synchronized (this) {
            list = subscribers.get(event.getClass());
            if (list == null || list.isEmpty()) {
                return;
            }
            list = new ArrayList<Subscription>(list);
        }

        for (int i = 0; i < list.size(); i++) {
            Subscription subscription = list.get(i);
            try {
                subscription.listener.handle(event);
            } catch (Throwable t) {
                System.err.println("[EventBus] Listener failed for " + event.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }

    private static final class Subscription {
        private final EventPriority priority;
        private final EventListener listener;

        private Subscription(EventPriority priority, EventListener listener) {
            this.priority = priority;
            this.listener = listener;
        }
    }
}
