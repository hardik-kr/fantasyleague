package com.cricket.fantasyleague.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * Serializes all {@link Long} values as JSON strings so 64-bit Snowflake user
 * ids survive JavaScript clients (Number.MAX_SAFE_INTEGER is 2^53 − 1).
 * Deserialization accepts both string and number for backward compatibility.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public SimpleModule longAsStringModule() {
        SimpleModule module = new SimpleModule("LongAsStringModule");
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        module.addDeserializer(Long.class, new LongDeserializer());
        return module;
    }

    @Bean
    public ObjectMapper objectMapper(SimpleModule longAsStringModule) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(longAsStringModule);
        return mapper;
    }

    private static final class LongDeserializer extends StdDeserializer<Long> {

        LongDeserializer() {
            super(Long.class);
        }

        @Override
        public Long deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
            JsonToken token = parser.currentToken();
            if (token == JsonToken.VALUE_STRING) {
                String text = parser.getText().trim();
                if (text.isEmpty()) {
                    return null;
                }
                return Long.parseLong(text);
            }
            if (token == JsonToken.VALUE_NUMBER_INT) {
                return parser.getLongValue();
            }
            return (Long) ctxt.handleUnexpectedToken(Long.class, parser);
        }
    }
}
