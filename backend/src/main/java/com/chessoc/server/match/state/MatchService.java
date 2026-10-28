package com.chessoc.server.match.state;

import com.chessoc.server.engine.ValidatorClient;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.concurrent.locks.ReentrantLock;

/** Owns the live games. Each match is created here and reached only through its {@link MatchGame}. */
public final class MatchService {

    private final ValidatorClient engine;
    private final MatchRepository repository;
    private final Scheduler scheduler;
    private final LongSupplier clock;
    private final Supplier<String> ids;
    private final Executor aiExecutor;
    private final Map<String, MatchGame> games = new ConcurrentHashMap<>();

    public MatchService(ValidatorClient engine, MatchRepository repository, Scheduler scheduler, LongSupplier clock) {
        this(engine, repository, scheduler, clock, () -> UUID.randomUUID().toString());
    }

    public MatchService(ValidatorClient engine, MatchRepository repository, Scheduler scheduler,
                        LongSupplier clock, Supplier<String> ids) {
        this(engine, repository, scheduler, clock, ids, SystemScheduler.sharedAiExecutor());
    }

    public MatchService(ValidatorClient engine, MatchRepository repository, Scheduler scheduler,
                        LongSupplier clock, Supplier<String> ids, Executor aiExecutor) {
        this.engine = engine;
        this.repository = repository;
        this.scheduler = scheduler;
        this.clock = clock;
        this.ids = ids;
        this.aiExecutor = aiExecutor;
    }

    public MatchGame create(GameSpec spec) {
        MatchGame game = new MatchGame(spec, engine, repository, scheduler, clock, ids,
                new ReentrantLock(), false, null, 0, aiExecutor);
        games.put(spec.gameId(), game);
        return game;
    }

    MatchGame createHeld(GameSpec spec, ReentrantLock matchLock) {
        MatchGame game = new MatchGame(spec, engine, repository, scheduler, clock, ids,
                matchLock, true, null, 0, aiExecutor);
        games.put(spec.gameId(), game);
        return game;
    }

    MatchGame restore(GameSpec spec, LiveRoomState.BoardState state, long savedAtEpochMs) {
        MatchGame game = new MatchGame(spec, engine, repository, scheduler, clock, ids,
                new ReentrantLock(), false, state, savedAtEpochMs, aiExecutor);
        games.put(spec.gameId(), game);
        return game;
    }

    MatchGame restoreHeld(GameSpec spec, ReentrantLock matchLock,
                          LiveRoomState.BoardState state, long savedAtEpochMs) {
        MatchGame game = new MatchGame(spec, engine, repository, scheduler, clock, ids,
                matchLock, true, state, savedAtEpochMs, aiExecutor);
        games.put(spec.gameId(), game);
        return game;
    }

    public Optional<MatchGame> get(String gameId) {
        return Optional.ofNullable(games.get(gameId));
    }

    /** Live (unfinished) games the account is a participant in. */
    public java.util.List<MatchGame> activeFor(String accountId) {
        return games.values().stream()
                .filter(g -> (accountId.equals(g.whiteId()) || accountId.equals(g.blackId())) && !g.isOver())
                .toList();
    }

    /** All live (unfinished) games — for spectator discovery. */
    public java.util.List<MatchGame> liveGames() {
        return games.values().stream().filter(g -> !g.isOver()).toList();
    }
}
