package com.chessoc.server.engine;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.PieceType;
import com.chessoc.server.domain.Side;
import com.chessoc.server.domain.Terminal;
import com.chessoc.server.domain.Uci;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Line-based validator wire codec: UCI-shaped setup lines out, JSON replies in. Pure translation —
 * no chess knowledge. An {@code error} reply is raised as a typed {@link EngineException}.
 */
public final class ValidatorProtocol {

    /** {@code position fen <fen> [moves ...]} — the move-list form carries history for threefold. */
    public String position(Fen start, List<Uci> moves) {
        StringBuilder sb = new StringBuilder("position fen ").append(start.raw());
        if (moves != null && !moves.isEmpty()) {
            sb.append(" moves");
            for (Uci m : moves) {
                sb.append(' ').append(m.value());
            }
        }
        return sb.toString();
    }

    public String applyRequest(Fen start, List<Uci> history, Uci move) {
        return position(start, history) + "\napply " + move.value();
    }

    public String legalMovesRequest(Fen start, List<Uci> history) {
        return position(start, history) + "\nlegalmoves";
    }

    public String statusRequest(Fen position) {
        return position(position, List.of()) + "\nstatus";
    }

    public String canMateRequest(Fen position, Side side) {
        return position(position, List.of()) + "\ncanmate " + side.toFen();
    }

    public ApplyResult parseApply(String json) {
        JsonNode n = EngineJson.read(json);
        raiseIfError(n);
        if (!n.path("legal").asBoolean(false)) {
            return ApplyResult.illegal(n.path("reason").asText("illegal"));
        }
        Fen fen = Fen.parse(EngineJson.required(n, "fen"));
        Terminal terminal = Terminal.fromWire(n.path("terminal").asText("none"));
        boolean inCheck = n.path("inCheck").asBoolean(false);
        Side stm = EngineJson.side(EngineJson.required(n, "sideToMove"));
        Optional<PieceType> drop = n.hasNonNull("capturedDropType")
                ? Optional.of(PieceType.fromSymbol(n.get("capturedDropType").asText().charAt(0)))
                : Optional.empty();
        return ApplyResult.legal(fen, terminal, inCheck, stm, drop);
    }

    public List<Uci> parseLegalMoves(String json) {
        JsonNode n = EngineJson.read(json);
        raiseIfError(n);
        List<Uci> moves = new ArrayList<>();
        for (JsonNode m : n.path("moves")) {
            moves.add(Uci.of(m.asText()));
        }
        return moves;
    }

    public StatusResult parseStatus(String json) {
        JsonNode n = EngineJson.read(json);
        raiseIfError(n);
        return new StatusResult(
                Terminal.fromWire(n.path("terminal").asText("none")),
                n.path("inCheck").asBoolean(false),
                EngineJson.side(EngineJson.required(n, "sideToMove")));
    }

    public boolean parseCanMate(String json) {
        JsonNode n = EngineJson.read(json);
        raiseIfError(n);
        return n.path("canMate").asBoolean(false);
    }

    private void raiseIfError(JsonNode n) {
        if (n.hasNonNull("error")) {
            throw new EngineException(EngineErrorCode.fromWire(n.get("error").asText()),
                    n.path("message").asText(""));
        }
    }
}
