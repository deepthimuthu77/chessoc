"""
chessoc engine service — a thin TCP front over the validator line protocol.

Two modes:
  * ENGINE_CMD set  -> proxy each connection to that binary's stdin/stdout (use the real vector64).
  * otherwise       -> a correct dev engine backed by python-chess (standard + crazyhouse).

Protocol (one request per query line; JSON reply):
  setoption name UCI_Variant value <variant>
  setoption name DrawRules value variant        (bughouse)
  position fen <fen...> [moves <uci> ...]
  apply <uci> | legalmoves | status | canmate <w|b>
"""

import json
import os
import shlex
import socketserver
import subprocess
import threading

import chess
import chess.variant

STANDARD_START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"


def build_board(variant, start_fen, moves):
    if variant in ("crazyhouse", "bughouse"):
        board = chess.variant.CrazyhouseBoard(start_fen)
    else:
        board = chess.Board(start_fen)
    for uci in moves:
        board.push_uci(uci)
    return board


def terminal(board, draw_rules_variant):
    if board.is_checkmate():
        return "checkmate"
    if board.is_stalemate():
        return "stalemate"
    if board.is_insufficient_material():
        return "draw_insufficient_material"
    if not draw_rules_variant and board.is_fifty_moves():
        return "draw_fifty_move"
    if not draw_rules_variant and board.is_repetition(3):
        return "draw_threefold"
    return "none"


def stm(board):
    return "w" if board.turn == chess.WHITE else "b"


PIECE_VAL = {chess.PAWN: 1, chess.KNIGHT: 3, chess.BISHOP: 3, chess.ROOK: 5, chess.QUEEN: 9, chess.KING: 0}


def material(board):
    score = 0
    for pt, val in PIECE_VAL.items():
        score += val * (len(board.pieces(pt, chess.WHITE)) - len(board.pieces(pt, chess.BLACK)))
    return score


def negamax(board, depth):
    if board.is_checkmate():
        return -1_000_000
    if board.is_game_over() or depth == 0:
        return (1 if board.turn == chess.WHITE else -1) * material(board)
    best = -10 ** 9
    for m in board.legal_moves:
        board.push(m)
        best = max(best, -negamax(board, depth - 1))
        board.pop()
    return best


def choose_move(board):
    """Weak 2-ply material search — legal but not strong (see docs on variant AI).

    Returns (best_move, side_relative_score) where the score is in pawns from the
    side-to-move's perspective.
    """
    best, best_score = None, -10 ** 9
    for m in board.legal_moves:
        board.push(m)
        score = -negamax(board, 1)
        board.pop()
        if score > best_score:
            best_score, best = score, m
    return best, best_score


class Conn:
    def __init__(self):
        self.variant = "standard"
        self.draw_rules_variant = False
        self.start_fen = STANDARD_START
        self.moves = []

    def handle(self, line):
        if line.startswith("setoption"):
            if "UCI_Variant" in line:
                self.variant = line.split()[-1].lower()
            if "DrawRules" in line and line.endswith("variant"):
                self.draw_rules_variant = True
            return None
        if line.startswith("position"):
            self._position(line.split())
            return None
        if line.startswith("legalmoves"):
            return self._legal_moves()
        if line.startswith("apply"):
            return self._apply(line.split()[1])
        if line.startswith("status"):
            return self._status()
        if line.startswith("canmate"):
            return self._can_mate(line.split()[1])
        if line.startswith("go"):
            return self._go()
        return None

    def _position(self, tokens):
        if len(tokens) >= 2 and tokens[1] == "startpos":
            self.start_fen = STANDARD_START
            rest = tokens[2:]
        else:
            self.start_fen = " ".join(tokens[2:8])
            rest = tokens[8:]
        self.moves = rest[1:] if rest and rest[0] == "moves" else []

    def _board(self):
        return build_board(self.variant, self.start_fen, self.moves)

    def _legal_moves(self):
        try:
            board = self._board()
            return json.dumps({"moves": [m.uci() for m in board.legal_moves]})
        except ValueError as e:
            return _malformed(e)

    def _status(self):
        try:
            board = self._board()
            return json.dumps({
                "terminal": terminal(board, self.draw_rules_variant),
                "inCheck": board.is_check(),
                "sideToMove": stm(board),
            })
        except ValueError as e:
            return _malformed(e)

    def _can_mate(self, side):
        try:
            color = chess.WHITE if side == "w" else chess.BLACK
            board = self._board()
            return json.dumps({"canMate": not board.has_insufficient_material(color)})
        except ValueError as e:
            return _malformed(e)

    def _go(self):
        try:
            board = self._board()
            move, raw = choose_move(board)
            if move is None:
                return json.dumps({"error": "internal_error", "message": "no legal moves"})
            if raw >= 100_000:
                score = {"mate": 1}
            elif raw <= -100_000:
                score = {"mate": -1}
            else:
                score = {"cp": int(raw * 100)}  # pawns → centipawns, side-relative
            return json.dumps({"bestMove": move.uci(), "score": score, "pv": [move.uci()], "depth": 2})
        except ValueError as e:
            return _malformed(e)

    def _apply(self, uci):
        try:
            board = self._board()
            move = chess.Move.from_uci(uci)
            if move not in board.legal_moves:
                return json.dumps({"legal": False, "reason": "illegal_move"})
            captured = self._captured_drop(board, move)
            board.push(move)
            return json.dumps({
                "legal": True,
                "fen": board.fen(),
                "terminal": terminal(board, self.draw_rules_variant),
                "inCheck": board.is_check(),
                "sideToMove": stm(board),
                "capturedDropType": captured,
            })
        except ValueError as e:
            return _malformed(e)

    def _captured_drop(self, board, move):
        if not board.is_capture(move):
            return None
        if board.is_en_passant(move):
            return "p"
        promoted = getattr(board, "promoted", 0)
        if promoted & chess.BB_SQUARES[move.to_square]:
            return "p"  # promotion revert: a captured promoted piece re-enters as a pawn
        piece_type = board.piece_type_at(move.to_square)
        return chess.piece_symbol(piece_type) if piece_type else None


def _malformed(error):
    return json.dumps({"error": "malformed_request", "message": str(error)})


class Handler(socketserver.StreamRequestHandler):
    def handle(self):
        engine_cmd = os.environ.get("ENGINE_CMD", "").strip()
        if engine_cmd:
            self._proxy(engine_cmd)
        else:
            self._dev()

    def _dev(self):
        conn = Conn()
        for raw in self.rfile:
            line = raw.decode("utf-8").strip()
            if not line:
                continue
            reply = conn.handle(line)
            if reply is not None:
                self.wfile.write((reply + "\n").encode("utf-8"))
                self.wfile.flush()

    def _proxy(self, engine_cmd):
        proc = subprocess.Popen(shlex.split(engine_cmd), stdin=subprocess.PIPE,
                                stdout=subprocess.PIPE, bufsize=1, text=True)

        def pump():
            for line in proc.stdout:
                self.wfile.write(line.encode("utf-8"))
                self.wfile.flush()

        threading.Thread(target=pump, daemon=True).start()
        for raw in self.rfile:
            proc.stdin.write(raw.decode("utf-8"))
            proc.stdin.flush()
        proc.terminate()


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


def main():
    port = int(os.environ.get("ENGINE_PORT", "9100"))
    with Server(("0.0.0.0", port), Handler) as server:
        mode = "proxy" if os.environ.get("ENGINE_CMD", "").strip() else "python-chess dev"
        print(f"chessoc engine ({mode}) listening on {port}", flush=True)
        server.serve_forever()


if __name__ == "__main__":
    main()
