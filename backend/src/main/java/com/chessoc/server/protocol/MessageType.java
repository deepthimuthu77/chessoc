package com.chessoc.server.protocol;

/** Wire {@code type} tokens for the live message catalog. */
public final class MessageType {

    private MessageType() {
    }

    // client -> server
    public static final String AUTH = "auth";
    public static final String INTENT = "intent";
    public static final String RESYNC = "resync";
    public static final String PING = "ping";
    public static final String CHAT = "chat"; // both directions

    // server -> client
    public static final String AUTH_OK = "auth_ok";
    public static final String AUTH_ERR = "auth_err";
    public static final String SNAPSHOT = "snapshot";
    public static final String CAPABILITIES = "capabilities";
    public static final String RECEIPT = "receipt";
    public static final String RESULT = "result";
    public static final String CLOCK = "clock";
    public static final String RESYNC_OK = "resync_ok";
    public static final String ERROR = "error";
    public static final String PONG = "pong";
    public static final String REMATCH_READY = "rematch_ready";
}
