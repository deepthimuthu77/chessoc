package com.chessoc.server.engine.adapter;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Side;
import com.chessoc.server.domain.Uci;
import com.chessoc.server.domain.Variant;
import com.chessoc.server.engine.ApplyResult;
import com.chessoc.server.engine.EngineAdapter;
import com.chessoc.server.engine.EngineErrorCode;
import com.chessoc.server.engine.EngineException;
import com.chessoc.server.engine.SearchBudget;
import com.chessoc.server.engine.SearchProtocol;
import com.chessoc.server.engine.SearchResult;
import com.chessoc.server.engine.StatusResult;
import com.chessoc.server.engine.ValidatorProtocol;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/**
 * The Engine Adapter: routes validator calls to the validator pool and search calls to the search
 * pool, composing the wire protocol and setting the variant per request. Pure translation — no rules.
 * An internal fault evicts the worker; a well-formed illegal/malformed reply keeps it.
 */
public final class PooledEngineAdapter implements EngineAdapter {

    private final EngineWorkerPool validators;
    private final EngineWorkerPool searchers;
    private final Duration validatorTimeout;
    private final Duration searchTimeout;
    private final ValidatorProtocol validatorProtocol = new ValidatorProtocol();
    private final SearchProtocol searchProtocol = new SearchProtocol();

    public PooledEngineAdapter(
            EngineWorkerPool validators,
            EngineWorkerPool searchers,
            Duration validatorTimeout,
            Duration searchTimeout) {
        this.validators = validators;
        this.searchers = searchers;
        this.validatorTimeout = validatorTimeout;
        this.searchTimeout = searchTimeout;
    }

    @Override
    public ApplyResult apply(Variant variant, Fen start, List<Uci> history, Uci move) {
        String payload = setup(variant) + "\n" + validatorProtocol.applyRequest(start, history, move);
        return call(validators, validatorTimeout, payload, validatorProtocol::parseApply);
    }

    @Override
    public List<Uci> legalMoves(Variant variant, Fen start, List<Uci> history) {
        String payload = setup(variant) + "\n" + validatorProtocol.legalMovesRequest(start, history);
        return call(validators, validatorTimeout, payload, validatorProtocol::parseLegalMoves);
    }

    @Override
    public StatusResult status(Variant variant, Fen position) {
        String payload = setup(variant) + "\n" + validatorProtocol.statusRequest(position);
        return call(validators, validatorTimeout, payload, validatorProtocol::parseStatus);
    }

    @Override
    public boolean canMate(Variant variant, Fen position, Side side) {
        String payload = setup(variant) + "\n" + validatorProtocol.canMateRequest(position, side);
        return call(validators, validatorTimeout, payload, validatorProtocol::parseCanMate);
    }

    @Override
    public SearchResult bestMove(Variant variant, Fen position, SearchBudget budget) {
        String payload = setup(variant) + "\n"
                + "setoption name Threads value 1\n"
                + "setoption name SearchJson value true\n"
                + validatorProtocol.position(position, List.of()) + "\n"
                + searchProtocol.go(budget);
        return call(searchers, searchTimeout, payload, searchProtocol::parse);
    }

    @Override
    public boolean isReady() {
        return validators.available() > 0;
    }

    @Override
    public void shutdown() {
        validators.close();
        searchers.close();
    }

    private String setup(Variant variant) {
        String line = "setoption name UCI_Variant value " + variant.name().toLowerCase();
        if (variant == Variant.BUGHOUSE) {
            line += "\nsetoption name DrawRules value variant";
        }
        return line;
    }

    private <T> T call(EngineWorkerPool pool, Duration timeout, String payload, Function<String, T> parse) {
        EngineWorker worker = pool.acquire();
        try {
            T result = parse.apply(worker.request(payload, timeout));
            pool.release(worker);
            return result;
        } catch (EngineException e) {
            if (e.code() == EngineErrorCode.INTERNAL_ERROR) {
                pool.evict(worker);
            } else {
                pool.release(worker);
            }
            throw e;
        } catch (RuntimeException e) {
            pool.release(worker);
            throw e;
        }
    }
}
