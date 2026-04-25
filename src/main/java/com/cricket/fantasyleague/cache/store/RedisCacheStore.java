package com.cricket.fantasyleague.cache.store;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisCacheStore<K, V> implements CacheStore<K, V> {

    private final StringRedisTemplate redisTemplate;
    private final HashOperations<String, String, String> hashOps;
    private final String hashKey;
    private final ObjectMapper mapper;
    private final JavaType valueType;
    private final Class<K> keyClass;

    public RedisCacheStore(StringRedisTemplate redisTemplate,
                           ObjectMapper mapper,
                           String namespace,
                           Class<K> keyClass,
                           Class<V> valueClass) {
        this.redisTemplate = redisTemplate;
        this.hashOps = redisTemplate.opsForHash();
        this.hashKey = "fantasy:" + namespace;
        this.mapper = mapper;
        this.valueType = mapper.getTypeFactory().constructType(valueClass);
        this.keyClass = keyClass;
    }

    @Override
    public V get(K key) {
        String json = hashOps.get(hashKey, key.toString());
        return json != null ? deserialize(json) : null;
    }

    @Override
    public void put(K key, V value) {
        hashOps.put(hashKey, key.toString(), serialize(value));
    }

    @Override
    public void putAll(Map<K, V> entries) {
        if (entries.isEmpty()) return;
        Map<String, String> serialized = new HashMap<>(entries.size());
        for (Map.Entry<K, V> e : entries.entrySet()) {
            serialized.put(e.getKey().toString(), serialize(e.getValue()));
        }
        hashOps.putAll(hashKey, serialized);
    }

    @Override
    public V remove(K key) {
        String json = hashOps.get(hashKey, key.toString());
        hashOps.delete(hashKey, key.toString());
        return json != null ? deserialize(json) : null;
    }

    @Override
    public boolean containsKey(K key) {
        return hashOps.hasKey(hashKey, key.toString());
    }

    @Override
    public Collection<V> values() {
        return hashOps.values(hashKey).stream()
                .map(this::deserialize)
                .toList();
    }

    @Override
    public Map<K, V> asMap() {
        Map<String, String> raw = hashOps.entries(hashKey);
        Map<K, V> result = new HashMap<>(raw.size());
        for (Map.Entry<String, String> e : raw.entrySet()) {
            result.put(parseKey(e.getKey()), deserialize(e.getValue()));
        }
        return result;
    }

    @Override
    public void clear() {
        redisTemplate.delete(hashKey);
    }

    @Override
    public int size() {
        Long s = hashOps.size(hashKey);
        return s != null ? s.intValue() : 0;
    }

    @Override
    public void forEachChunk(int chunkSize, BiConsumer<List<K>, List<V>> handler) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive: " + chunkSize);
        }
        ScanOptions opts = ScanOptions.scanOptions().count(chunkSize).build();
        List<K> keys = new ArrayList<>(chunkSize);
        List<V> values = new ArrayList<>(chunkSize);
        try (Cursor<Map.Entry<String, String>> cursor = hashOps.scan(hashKey, opts)) {
            while (cursor.hasNext()) {
                Map.Entry<String, String> e = cursor.next();
                keys.add(parseKey(e.getKey()));
                values.add(deserialize(e.getValue()));
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

    @SuppressWarnings("unchecked")
    private K parseKey(String raw) {
        if (keyClass == Long.class) return (K) Long.valueOf(raw);
        if (keyClass == Integer.class) return (K) Integer.valueOf(raw);
        if (keyClass == String.class) return (K) raw;
        return (K) raw;
    }

    private String serialize(V value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Redis cache serialize failed for " + hashKey, e);
        }
    }

    private V deserialize(String json) {
        try {
            return mapper.readValue(json, valueType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Redis cache deserialize failed for " + hashKey, e);
        }
    }
}
