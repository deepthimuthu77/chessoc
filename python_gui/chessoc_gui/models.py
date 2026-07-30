from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class MoveAction:
    uci: str
    san: str
    next_fen: str
    capture_square: str | None = None
    check: bool = False
    active_clock: str = "white"


@dataclass(frozen=True)
class SequenceStep:
    label: str
    type: str
    delay_ms: int = 600
    move: MoveAction | None = None
    title: str | None = None
    body: str | None = None
    active_clock: str | None = None
