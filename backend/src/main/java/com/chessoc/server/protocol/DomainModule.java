package com.chessoc.server.protocol;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Side;
import com.chessoc.server.domain.Uci;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;

/** Wire binding for domain value types: Side as white/black, Uci and Fen as their strings. */
public final class DomainModule extends SimpleModule {

    public DomainModule() {
        addSerializer(Side.class, new JsonSerializer<>() {
            @Override
            public void serialize(Side v, JsonGenerator g, SerializerProvider p) throws IOException {
                g.writeString(v.name().toLowerCase());
            }
        });
        addDeserializer(Side.class, new JsonDeserializer<>() {
            @Override
            public Side deserialize(JsonParser p, DeserializationContext c) throws IOException {
                return Side.valueOf(p.getValueAsString().toUpperCase());
            }
        });

        addSerializer(Uci.class, new JsonSerializer<>() {
            @Override
            public void serialize(Uci v, JsonGenerator g, SerializerProvider p) throws IOException {
                g.writeString(v.value());
            }
        });
        addDeserializer(Uci.class, new JsonDeserializer<>() {
            @Override
            public Uci deserialize(JsonParser p, DeserializationContext c) throws IOException {
                return Uci.of(p.getValueAsString());
            }
        });

        addSerializer(Fen.class, new JsonSerializer<>() {
            @Override
            public void serialize(Fen v, JsonGenerator g, SerializerProvider p) throws IOException {
                g.writeString(v.raw());
            }
        });
        addDeserializer(Fen.class, new JsonDeserializer<>() {
            @Override
            public Fen deserialize(JsonParser p, DeserializationContext c) throws IOException {
                return Fen.parse(p.getValueAsString());
            }
        });
    }
}
