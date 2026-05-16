package com.cricket.fantasyleague.cache.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.cricket.fantasyleague.payload.response.MatchResponse;
import com.cricket.fantasyleague.payload.response.TeamBrief;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Smoke that Redis-backed store (strategy 2) round-trips per-match {@link MatchResponse} JSON.
 */
@ExtendWith(MockitoExtension.class)
class RedisCacheStoreMasterPayloadTest {

    @Mock
    private StringRedisTemplate template;
    @Mock
    private HashOperations<String, Object, Object> hashOps;

    @Test
    void roundTripSingleMatchResponse() {
        when(template.opsForHash()).thenReturn(hashOps);

        ObjectMapper mapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        RedisCacheStore<Integer, MatchResponse> store =
                new RedisCacheStore<>(template, mapper, "master:matches:v1", Integer.class, MatchResponse.class);

        MatchResponse mr = new MatchResponse(
                9, LocalDate.of(2026, 5, 1), LocalTime.NOON, "v", "t", "r", false,
                "UPCOMING", "d", new TeamBrief(1, "A", "AA"), new TeamBrief(2, "B", "BB"));
        store.put(9, mr);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(hashOps).put(eq("fantasy:master:matches:v1"), eq("9"), jsonCaptor.capture());
        when(hashOps.get("fantasy:master:matches:v1", "9")).thenReturn(jsonCaptor.getValue());

        assertEquals(9, store.get(9).id());
    }

    @Test
    void cacheStoreFactoryStrategy2ReturnsRedisStore() {
        CacheStoreFactory factory = new CacheStoreFactory();
        ReflectionTestUtils.setField(factory, "strategy", 2);
        ReflectionTestUtils.setField(factory, "stringRedisTemplate", template);
        when(template.opsForHash()).thenReturn(hashOps);
        ReflectionTestUtils.invokeMethod(factory, "init");

        CacheStore<Integer, MatchResponse> store =
                factory.create("master:matches:v1", Integer.class, MatchResponse.class);
        assertInstanceOf(RedisCacheStore.class, store);
    }
}
