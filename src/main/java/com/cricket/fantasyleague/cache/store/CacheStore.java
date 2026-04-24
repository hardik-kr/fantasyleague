package com.cricket.fantasyleague.cache.store;

import java.util.Collection;
import java.util.Map;

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
}
