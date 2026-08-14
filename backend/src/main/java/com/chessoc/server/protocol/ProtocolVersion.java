package com.chessoc.server.protocol;

/** Wire protocol version [major, minor]. An incompatible major is refused, never guessed. */
public record ProtocolVersion(int major, int minor) {

    public static final ProtocolVersion CURRENT = new ProtocolVersion(1, 0);

    public boolean isCompatibleWith(ProtocolVersion other) {
        return this.major == other.major;
    }
}
