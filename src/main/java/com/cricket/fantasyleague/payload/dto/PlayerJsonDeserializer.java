package com.cricket.fantasyleague.payload.dto;

import java.io.IOException;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

public class PlayerJsonDeserializer extends StdDeserializer<PlayerJsonListDto>
{
    public PlayerJsonDeserializer() 
    {
        this(null) ;
    }

    public PlayerJsonDeserializer(Class<?> vc) 
    {
        super(vc);
    }

    @Override
    public PlayerJsonListDto deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JacksonException 
    {
        try {
            JsonNode node = jp.getCodec().readTree(jp);
            JsonNode playernodelist = node.get("Data").get("Value").get("Players") ;
        
        } catch (Exception e) {

        }
        return null ;
    }
    
}
