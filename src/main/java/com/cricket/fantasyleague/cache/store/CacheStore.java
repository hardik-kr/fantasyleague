package com.cricket.fantasyleague.cache.store;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Pluggable key-value store used by the live-match caches.
 * Strategy 1 = in-memory (ConcurrentHashMap), Strategy 2 = Redis.
 */
public interface CacheStore<K, V> {

    V get(K key);

    void put(K key, V value);

    void putAll(Map<K, V> entries);

    V remove(K key);

    boolean containsKey(K key);

    Collection<V> values();

    Map<K, V> asMap();

    void clear();

    int size();

    /**
     * Streams the full contents of the store in bounded chunks, invoking the
     * handler once per chunk with parallel key/value lists. The handler's
     * lists must not be retained beyond the callback — implementations may
     * clear and reuse them across chunks.
     *
     * <p>Enables processing large caches (100K+ entries) with bounded heap
     * footprint ({@code O(chunkSize)}), avoiding humongous map allocations.
     */
    void forEachChunk(int chunkSize, BiConsumer<List<K>, List<V>> handler);
}
