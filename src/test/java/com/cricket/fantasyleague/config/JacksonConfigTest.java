package com.cricket.fantasyleague.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cricket.fantasyleague.payload.response.UserProfileResponse;

class JacksonConfigTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JacksonConfig().longAsStringModule());
    }

    @Test
    void serializesLongIdAsString() throws Exception {
        var profile = new UserProfileResponse(
                191886327761866752L,
                "RoyalRocks",
                "HARDIK",
                "KUMAR",
                "hardik@example.com",
                "INDW",
                true,
                421.0,
                7,
                55);

        String json = objectMapper.writeValueAsString(profile);

        assertTrue(json.contains("\"id\":\"191886327761866752\""));
    }

    @Test
    void deserializesStringOrNumberLong() throws Exception {
        Long fromString = objectMapper.readValue("{\"userId\":\"191886327761866752\"}", Wrapper.class).userId();
        Long fromNumber = objectMapper.readValue("{\"userId\":191886327761866752}", Wrapper.class).userId();

        assertEquals(191886327761866752L, fromString);
        assertEquals(191886327761866752L, fromNumber);
    }

    private record Wrapper(Long userId) {
    }
}
