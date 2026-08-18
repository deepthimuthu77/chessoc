package com.chessoc.server.session;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Short-lived, single-use tickets for opening the live connection (§9). A ticket is consumed on use —
 * presenting it a second time fails.
 */
public final class TicketService {

    private record Ticket(String sessionId, long expiresAtEpochMs) {
    }

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private final Supplier<String> idGenerator;
    private final long ttlMs;

    public TicketService(LongSupplier clock, long ttlMs) {
        this(clock, ttlMs, () -> UUID.randomUUID().toString());
    }

    public TicketService(LongSupplier clock, long ttlMs, Supplier<String> idGenerator) {
        this.clock = clock;
        this.ttlMs = ttlMs;
        this.idGenerator = idGenerator;
    }

    public String issue(String sessionId) {
        String id = idGenerator.get();
        tickets.put(id, new Ticket(sessionId, clock.getAsLong() + ttlMs));
        return id;
    }

    public String consume(String ticket) {
        Ticket entry = tickets.remove(ticket);
        if (entry == null) {
            throw new AuthException("unknown or spent ticket");
        }
        if (clock.getAsLong() >= entry.expiresAtEpochMs()) {
            throw new AuthException("ticket expired");
        }
        return entry.sessionId();
    }
}
