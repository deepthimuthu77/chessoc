package com.chessoc.server.protocol;

/** The peer speaks an incompatible major version; the connection must be refused. */
public class IncompatibleProtocolVersionException extends ProtocolException {

    private final ProtocolVersion supported;
    private final ProtocolVersion received;

    public IncompatibleProtocolVersionException(ProtocolVersion supported, ProtocolVersion received) {
        super("incompatible protocol: supported " + supported + ", received " + received);
        this.supported = supported;
        this.received = received;
    }

    public ProtocolVersion supported() {
        return supported;
    }

    public ProtocolVersion received() {
        return received;
    }
}
