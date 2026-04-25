package com.cricket.fantasyleague.cache.store;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class InMemoryCacheStore<K, V> implements CacheStore<K, V> {

    private final ConcurrentHashMap<K, V> map = new ConcurrentHashMap<>();

    @Override
    public V get(K key) {
        return map.get(key);
    }

    @Override
    public void put(K key, V value) {
        map.put(key, value);
    }

    @Override
    public void putAll(Map<K, V> entries) {
        map.putAll(entries);
    }

    @Override
    public V remove(K key) {
        return map.remove(key);
    }

    @Override
    public boolean containsKey(K key) {
        return map.containsKey(key);
    }

    @Override
    public Collection<V> values() {
        return Collections.unmodifiableCollection(map.values());
    }

    @Override
    public Map<K, V> asMap() {
        return Collections.unmodifiableMap(map);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public void forEachChunk(int chunkSize, BiConsumer<List<K>, List<V>> handler) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive: " + chunkSize);
        }
        List<K> keys = new ArrayList<>(chunkSize);
        List<V> values = new ArrayList<>(chunkSize);
        for (Map.Entry<K, V> e : map.entrySet()) {
            keys.add(e.getKey());
            values.add(e.getValue());
            if (keys.size() >= chunkSize) {
                handler.accept(keys, values);
                keys.clear();
                values.clear();
            }
        }
        if (!keys.isEmpty()) {
            handler.accept(keys, values);
        }
    }
}
