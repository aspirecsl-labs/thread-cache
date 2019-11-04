package com.aspirecsl.labs;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.locks.StampedLock;

import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toMap;

/**
 * Short Description
 *
 * <p>Full Description
 *
 * @author anoopr
 */
public class ThreadCache {

    private static final StampedLock lock = new StampedLock();

    private static final ThreadLocal<Tag> tag = new ThreadLocal<>();

    private static final Map<String, Map<Class<?>, Map<String, Object>>> CACHE = new WeakHashMap<>();

    public static void initialise() {
        tag.set(new Tag(randomUUID().toString()));
    }

    public static void close() {
        if (isInitialised()) {
            tag.get().value = null;
            tag.remove();
        }
    }

    public static <T> void add(String componentKey, T component) {
        final long stamp = lock.writeLock();
        try {
            CACHE.computeIfAbsent(ThreadCache.tag(), k -> new HashMap<>())
                    .computeIfAbsent(component.getClass(), k -> new HashMap<>()).put(componentKey, component);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public static <T> T get(Class<T> containerKey, String componentKey) {
        long stamp = lock.tryOptimisticRead();
        Map<String, T> resources = getResourcesFromContext(containerKey);
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                resources = getResourcesFromContext(containerKey);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return Optional.ofNullable(resources.get(componentKey))
                .orElseThrow(() -> new RuntimeException("no value stored for key: " + componentKey));
    }

    public static <T> T remove(Class<T> containerKey, String componentKey) {
        final long stamp = lock.writeLock();
        try {
            return getResourcesFromContext(containerKey).remove(componentKey);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public static <T> int length(Class<T> key) {
        long stamp = lock.tryOptimisticRead();
        int length =
                Optional.ofNullable(CACHE.get(ThreadCache.tag()))
                        .map(e -> e.get(key))
                        .map(Map::size)
                        .orElse(0);
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                length =
                        Optional.ofNullable(CACHE.get(ThreadCache.tag()))
                                .map(e -> e.get(key))
                                .map(Map::size)
                                .orElse(0);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return length;
    }

    public static boolean isEmpty() {
        long stamp = lock.tryOptimisticRead();
        boolean isEmpty = CACHE.isEmpty();
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                isEmpty = CACHE.isEmpty();
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return isEmpty;
    }

    private static <T> Map<String, T> getResourcesFromContext(Class<T> containerKey) {
        return Optional.ofNullable(CACHE.get(ThreadCache.tag()))
                .map(e -> e.get(containerKey))
                .map(e -> e.entrySet().stream().collect(toMap(Entry::getKey, entry -> containerKey.cast(entry.getValue()))))
                .orElseThrow(() -> new IllegalStateException(
                        "Context doesn't have a resource for key: [" + containerKey.getSimpleName() + "]!"));
    }

    private static String tag() {
        return Optional.ofNullable(tag.get())
                .map(e -> e.value)
                .orElseThrow(() -> new IllegalStateException("Context not initialised!\n"));
    }

    private static boolean isInitialised() {
        return tag.get() != null;
    }

    /**
     * Holds the key for a <tt>WeakHashMap</tt> container that acts as the cache
     */
    private static class Tag {
        private String value;

        private Tag(String value) {
            this.value = "" + value;
        }
    }
}
