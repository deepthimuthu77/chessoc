from __future__ import annotations

from .models import MoveAction, SequenceStep


INITIAL_FEN = "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 0 3"

WHITE_CASTLE = MoveAction(
    uci="e1g1",
    san="O-O",
    next_fen="r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQ1RK1 b kq - 1 3",
    active_clock="black",
)
BLACK_KNIGHT = MoveAction(
    uci="g8f6",
    san="Nf6",
    next_fen="r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQ1RK1 w kq - 2 4",
    active_clock="white",
)
WHITE_BISHOP_STRIKE = MoveAction(
    uci="c4f7",
    san="Bxf7+",
    next_fen="r1bqkb1r/pppp1Bpp/2n2n2/4p3/4P3/5N2/PPPP1PPP/RNBQ1RK1 b kq - 0 4",
    capture_square="f7",
    check=True,
    active_clock="black",
)

SCRIPT = [
    SequenceStep(
        label="Opening settles into an attacking shape",
        type="focus",
        delay_ms=700,
        active_clock="white",
    ),
    SequenceStep(
        label="White castles kingside",
        type="move",
        move=WHITE_CASTLE,
        active_clock="black",
    ),
    SequenceStep(
        label="Black develops the knight",
        type="move",
        move=BLACK_KNIGHT,
        active_clock="white",
    ),
    SequenceStep(
        label="White lands a capture with a sharper hit",
        type="move",
        move=WHITE_BISHOP_STRIKE,
        active_clock="black",
    ),
    SequenceStep(
        label="Check state pulses through the board",
        type="pulse",
        delay_ms=850,
    ),
    SequenceStep(
        label="Final reveal style for result presentation",
        type="result",
        title="Checkmate",
        body="Ceremonial overlay after the decisive sequence.",
    ),
]

# Manual interaction path while the full rules engine is not yet wired into the GUI.
INTERACTIVE_LINE = [
    WHITE_CASTLE,
    BLACK_KNIGHT,
    WHITE_BISHOP_STRIKE,
]
