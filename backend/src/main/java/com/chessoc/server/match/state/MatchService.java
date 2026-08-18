package com.chessoc.server.match.state;

import com.chessoc.server.engine.ValidatorClient;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Owns the live games. Each match is created here and reached only through its {@link MatchGame}. */
public final class MatchService {

    private final ValidatorClient engine;
    private final MatchRepository repository;
    private final Scheduler scheduler;
    private final LongSupplier clock;
    private final Supplier<String> ids;
    private final Map<String, MatchGame> games = new ConcurrentHashMap<>();

    public MatchService(ValidatorClient engine, MatchRepository repository, Scheduler scheduler, LongSupplier clock) {
        this(engine, repository, scheduler, clock, () -> UUID.randomUUID().toString());
    }

    public MatchService(ValidatorClient engine, MatchRepository repository, Scheduler scheduler,
                        LongSupplier clock, Supplier<String> ids) {
        this.engine = engine;
        this.repository = repository;
        this.scheduler = scheduler;
        this.clock = clock;
        this.ids = ids;
    }

    public MatchGame create(GameSpec spec) {
        MatchGame game = new MatchGame(spec, engine, repository, scheduler, clock, ids);
        games.put(spec.gameId(), game);
        return game;
    }

    public Optional<MatchGame> get(String gameId) {
        return Optional.ofNullable(games.get(gameId));
    }
}
