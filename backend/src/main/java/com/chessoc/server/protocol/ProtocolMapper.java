package com.chessoc.server.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

/** Builds the ObjectMapper used for the client wire protocol. */
public final class ProtocolMapper {

    private ProtocolMapper() {
    }

    public static ObjectMapper create() {
        return new ObjectMapper()
                .registerModule(new Jdk8Module())
                .registerModule(new DomainModule());
    }
}
