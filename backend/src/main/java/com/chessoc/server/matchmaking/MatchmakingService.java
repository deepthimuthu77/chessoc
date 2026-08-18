package com.chessoc.server.matchmaking;

import com.chessoc.server.domain.Variant;
import com.chessoc.server.match.TimeControl;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Queue-based pairing bucketed by variant × speed, plus friend challenges. Bughouse is unrated in v1.
 * Colour assignment is delegated so it can be made deterministic in tests.
 */
public final class MatchmakingService {

    public record Challenge(String code, String fromPlayerId, Variant variant, TimeControl timeControl, boolean rated) {
    }

    private final Map<Seek.Bucket, Deque<Seek>> queues = new ConcurrentHashMap<>();
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final BooleanSupplier firstIsWhite;
    private final Supplier<String> codes;

    public MatchmakingService() {
        this(() -> Math.random() < 0.5, () -> UUID.randomUUID().toString().substring(0, 8));
    }

    public MatchmakingService(BooleanSupplier firstIsWhite, Supplier<String> codes) {
        this.firstIsWhite = firstIsWhite;
        this.codes = codes;
    }

    /** Add a seek; if a compatible opponent is already waiting, pair immediately. */
    public synchronized Optional<Pairing> enqueue(Seek seek) {
        Deque<Seek> queue = queues.computeIfAbsent(seek.bucket(), k -> new ArrayDeque<>());
        Seek waiting = queue.peekFirst();
        if (waiting != null && !waiting.playerId().equals(seek.playerId())) {
            queue.pollFirst();
            return Optional.of(pair(waiting, seek, seek.variant() != Variant.BUGHOUSE));
        }
        if (waiting == null) {
            queue.addLast(seek);
        }
        return Optional.empty();
    }

    public synchronized void cancel(String playerId) {
        queues.values().forEach(q -> q.removeIf(s -> s.playerId().equals(playerId)));
    }

    public Challenge createChallenge(String fromPlayerId, Variant variant, TimeControl timeControl) {
        Challenge challenge = new Challenge(codes.get(), fromPlayerId, variant, timeControl,
                variant != Variant.BUGHOUSE);
        challenges.put(challenge.code(), challenge);
        return challenge;
    }

    public Optional<Pairing> accept(String code, String accepterId) {
        Challenge challenge = challenges.remove(code);
        if (challenge == null || challenge.fromPlayerId().equals(accepterId)) {
            return Optional.empty();
        }
        Seek from = new Seek(challenge.fromPlayerId(), challenge.variant(), challenge.timeControl());
        Seek to = new Seek(accepterId, challenge.variant(), challenge.timeControl());
        return Optional.of(pair(from, to, challenge.rated()));
    }

    private Pairing pair(Seek a, Seek b, boolean rated) {
        Seek whiteSeek = firstIsWhite.getAsBoolean() ? a : b;
        Seek blackSeek = whiteSeek == a ? b : a;
        return new Pairing(whiteSeek.playerId(), blackSeek.playerId(), a.variant(), a.timeControl(), rated);
    }
}
