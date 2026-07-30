from __future__ import annotations

import time
from dataclasses import dataclass

import pygame

from . import theme
from .board_view import BoardView
from .controls import Button
from .fen import parse_board
from .models import MoveAction, SequenceStep
from .sequence import BLACK_KNIGHT, INITIAL_FEN, INTERACTIVE_LINE, SCRIPT


WINDOW_SIZE = (1460, 940)
FPS = 60


@dataclass
class AnimationState:
    piece: str
    from_square: str
    to_square: str
    capture_square: str | None
    started_at: float
    duration: float
    on_complete: callable


class ChessocApp:
    def __init__(self) -> None:
        pygame.init()
        pygame.display.set_caption("Chessoc")
        self.screen = pygame.display.set_mode(WINDOW_SIZE)
        self.clock = pygame.time.Clock()
        self.fonts = self._build_fonts()
        self.board_view = BoardView(origin=(368, 142), square_size=78)

        self.replay_button = Button("Replay Sequence", pygame.Rect(44, 258, 240, 46))
        self.reset_button = Button("Reset Interactive Line", pygame.Rect(44, 316, 240, 46))

        self.running = True
        self.script_running = False
        self.script_index = 0
        self.next_script_at = 0.0
        self.animation: AnimationState | None = None
        self.hovered_button: str | None = None
        self.check_pulse_until = 0.0
        self.overlay: tuple[str, str] | None = None
        self.overlay_visible = False

        self.current_fen = INITIAL_FEN
        self.current_board = parse_board(self.current_fen)
        self.current_move_rows = [("1", "e4", "e5"), ("2", "Nf3", "Nc6"), ("3", "Bc4", "")]
        self.last_move: tuple[str, str] | None = None
        self.selected_square: str | None = None
        self.target_squares: set[str] = set()
        self.interaction_stage = 0
        self.active_clock = "white"
        self.mode_text = "Interaction mode: click e1 to begin"
        self.sequence_selection: int | None = None

    def _build_fonts(self) -> dict[str, pygame.font.Font]:
        return {
            "eyebrow": pygame.font.SysFont("avenir next", 12, bold=True),
            "body": pygame.font.SysFont("avenir next", 20),
            "small": pygame.font.SysFont("avenir next", 16),
            "coord": pygame.font.SysFont("avenir next", 11),
            "title": pygame.font.SysFont("palatino", 38, bold=True),
            "subtitle": pygame.font.SysFont("palatino", 24, bold=True),
            "button": pygame.font.SysFont("avenir next", 18, bold=True),
            "clock": pygame.font.SysFont("avenir next", 28, bold=True),
            "piece": pygame.font.SysFont("times new roman", 46),
            "overlay": pygame.font.SysFont("palatino", 44, bold=True),
        }

    def run(self) -> None:
        while self.running:
            self._handle_events()
            self._update()
            self._draw()
            self.clock.tick(FPS)
        pygame.quit()

    def _handle_events(self) -> None:
        mouse_pos = pygame.mouse.get_pos()
        self.hovered_button = None
        if self.replay_button.contains(mouse_pos):
            self.hovered_button = "replay"
        elif self.reset_button.contains(mouse_pos):
            self.hovered_button = "reset"

        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                self.running = False
                return
            if event.type == pygame.MOUSEBUTTONDOWN and event.button == 1:
                self._handle_click(event.pos)

    def _handle_click(self, position: tuple[int, int]) -> None:
        if self.replay_button.contains(position):
            self.replay_sequence()
            return
        if self.reset_button.contains(position):
            self.reset_interactive_line()
            return

        square = self.board_view.square_at(position)
        if square is None or self.script_running or self.animation is not None:
            return

        expected_action = self._expected_player_action()
        if expected_action is None:
            self.mode_text = "Line complete: replay or reset to continue"
            return

        source_square = expected_action.uci[:2]
        target_square = expected_action.uci[2:4]

        if self.selected_square is None:
            if square != source_square:
                self.mode_text = f"Select {source_square} to continue the line"
                return
            self.selected_square = square
            self.target_squares = {target_square}
            self.mode_text = f"Move {source_square} to {target_square}"
            return

        if square == self.selected_square:
            self.selected_square = None
            self.target_squares = set()
            self.mode_text = "Selection cleared"
            return

        if square != target_square:
            self.mode_text = f"That line expects {target_square}"
            return

        self.selected_square = None
        self.target_squares = set()
        self._begin_action(expected_action, self._after_player_action)

    def replay_sequence(self) -> None:
        if self.script_running:
            return
        self.reset_view()
        self.script_running = True
        self.script_index = 0
        self.sequence_selection = None
        self.mode_text = "Autoplay sequence running"
        self.next_script_at = time.monotonic()

    def reset_interactive_line(self) -> None:
        if self.script_running:
            return
        self.reset_view()

    def reset_view(self) -> None:
        self.current_fen = INITIAL_FEN
        self.current_board = parse_board(self.current_fen)
        self.current_move_rows = [("1", "e4", "e5"), ("2", "Nf3", "Nc6"), ("3", "Bc4", "")]
        self.last_move = None
        self.selected_square = None
        self.target_squares = set()
        self.interaction_stage = 0
        self.active_clock = "white"
        self.mode_text = "Interaction mode: click e1 to begin"
        self.sequence_selection = None
        self.overlay_visible = False
        self.overlay = None
        self.check_pulse_until = 0.0
        self.animation = None
        self.script_running = False

    def _update(self) -> None:
        now = time.monotonic()
        if self.script_running and self.animation is None and now >= self.next_script_at:
            self._advance_script()

        if self.animation is not None:
            elapsed = now - self.animation.started_at
            if elapsed >= self.animation.duration:
                callback = self.animation.on_complete
                self.animation = None
                callback()

    def _advance_script(self) -> None:
        if self.script_index >= len(SCRIPT):
            self.script_running = False
            self.mode_text = "Interaction mode: click e1 to begin"
            self.sequence_selection = None
            return

        step = SCRIPT[self.script_index]
        self.sequence_selection = self.script_index
        if step.active_clock:
            self.active_clock = step.active_clock

        if step.type == "focus":
            self.next_script_at = time.monotonic() + step.delay_ms / 1000
            self.script_index += 1
            return

        if step.type == "pulse":
            self.check_pulse_until = time.monotonic() + 0.55
            self.next_script_at = time.monotonic() + step.delay_ms / 1000
            self.script_index += 1
            return

        if step.type == "result":
            self.overlay = (step.title or "Result", step.body or "")
            self.overlay_visible = True
            self.next_script_at = time.monotonic() + step.delay_ms / 1000
            self.script_index += 1
            return

        if step.type == "move" and step.move is not None:
            self._begin_action(step.move, self._after_script_move)

    def _after_script_move(self) -> None:
        self.script_index += 1
        self.next_script_at = time.monotonic() + 0.35

    def _begin_action(self, action: MoveAction, on_complete: callable) -> None:
        self.active_clock = action.active_clock
        piece = self.current_board.get(action.uci[:2])
        if piece is None:
            on_complete()
            return
        self.animation = AnimationState(
            piece=piece,
            from_square=action.uci[:2],
            to_square=action.uci[2:4],
            capture_square=action.capture_square,
            started_at=time.monotonic(),
            duration=0.52,
            on_complete=lambda: self._finish_action(action, on_complete),
        )

    def _finish_action(self, action: MoveAction, on_complete: callable) -> None:
        self.current_fen = action.next_fen
        self.current_board = parse_board(self.current_fen)
        self.last_move = (action.uci[:2], action.uci[2:4])
        self._append_move(action.san)
        if action.check:
            self.check_pulse_until = time.monotonic() + 0.55
        on_complete()

    def _after_player_action(self) -> None:
        if self.interaction_stage == 0:
            self.interaction_stage = 1
            self.mode_text = "Black reply is animating"
            self._begin_action(BLACK_KNIGHT, self._after_black_reply)
            return
        if self.interaction_stage == 2:
            self.interaction_stage = 3
            self.mode_text = "Interactive line complete"
            self.overlay = ("Attack Landed", "The bishop strike, check pulse, and finish treatment are ready for refinement.")
            self.overlay_visible = True
            self.check_pulse_until = time.monotonic() + 0.55

    def _after_black_reply(self) -> None:
        self.interaction_stage = 2
        self.mode_text = "White to move: click c4 then f7"

    def _expected_player_action(self) -> MoveAction | None:
        if self.interaction_stage == 0:
            return INTERACTIVE_LINE[0]
        if self.interaction_stage == 2:
            return INTERACTIVE_LINE[2]
        return None

    def _append_move(self, san: str) -> None:
        if not self.current_move_rows:
            self.current_move_rows.append(("1", san, ""))
            return
        number, white, black = self.current_move_rows[-1]
        if white and not black:
            self.current_move_rows[-1] = (number, white, san)
        else:
            self.current_move_rows.append((str(len(self.current_move_rows) + 1), san, ""))

    def _draw(self) -> None:
        self.screen.fill(theme.SHELL_BG)
        self._draw_panels()
        pulse = time.monotonic() < self.check_pulse_until
        hidden = set()
        capture_square = None

        if self.animation is not None:
            hidden = {self.animation.from_square}
            if self.animation.capture_square:
                hidden.add(self.animation.capture_square)
            capture_square = self.animation.capture_square

        self.board_view.draw(
            self.screen,
            self.fonts,
            self.current_board,
            last_move=self.last_move,
            selected_square=self.selected_square,
            target_squares=self.target_squares,
            capture_square=capture_square,
            hidden_squares=hidden,
            pulse=pulse,
        )
        if self.animation is not None:
            progress = min((time.monotonic() - self.animation.started_at) / self.animation.duration, 1.0)
            sx, sy = self.board_view.center_of(self.animation.from_square)
            ex, ey = self.board_view.center_of(self.animation.to_square)
            x = sx + (ex - sx) * progress
            lift = -18 * (1 - abs(0.5 - progress) * 2)
            y = sy + (ey - sy) * progress + lift
            self.board_view.draw_ghost(self.screen, self.animation.piece, (x, y), self.fonts["piece"])

        if self.overlay_visible and self.overlay is not None:
            self._draw_overlay(*self.overlay)

        pygame.display.flip()

    def _draw_panels(self) -> None:
        self._rounded_panel(pygame.Rect(24, 24, 280, 892))
        self._rounded_panel(pygame.Rect(1156, 24, 280, 892))
        self._rounded_panel(pygame.Rect(344, 24, 780, 92))
        self._rounded_panel(pygame.Rect(344, 798, 780, 92))

        self.replay_button.draw(self.screen, self.fonts["button"], self.hovered_button == "replay")
        self.reset_button.draw(self.screen, self.fonts["button"], self.hovered_button == "reset")

        self._text("Chessoc", self.fonts["eyebrow"], theme.MUTED, (44, 44))
        self._text("Pygame Board Study", self.fonts["title"], theme.INK, (44, 66))
        self._multiline(
            "Real app structure in Python with a reusable board renderer, event loop, and interaction-ready animation state.",
            self.fonts["body"],
            theme.MUTED,
            pygame.Rect(44, 132, 240, 90),
        )
        self._text(self.mode_text, self.fonts["small"], theme.MUTED, (44, 384))

        self._text("Sequence", self.fonts["subtitle"], theme.INK, (44, 440))
        self._draw_sequence_list()

        self._draw_player_banner("Black", "Vector", "04:58", pygame.Rect(344, 24, 780, 92), self.active_clock == "black")
        self._draw_player_banner("White", "Deepthi", "05:00", pygame.Rect(344, 798, 780, 92), self.active_clock == "white")

        self._text("Moves", self.fonts["subtitle"], theme.INK, (1178, 44))
        self._draw_move_list()
        self._text("Interaction Goals", self.fonts["subtitle"], theme.INK, (1178, 328))
        goals = [
            "Interactive white move path while transport is still pending.",
            "Single render loop for clocks, board, highlights, and overlays.",
            "Animation state separated from board-state mutation.",
            "Easy transition to live engine-backed move events later.",
        ]
        top = 372
        for line in goals:
            self._multiline(f"• {line}", self.fonts["small"], theme.MUTED, pygame.Rect(1178, top, 220, 72))
            top += 72

    def _draw_sequence_list(self) -> None:
        y = 488
        for index, step in enumerate(SCRIPT):
            row_rect = pygame.Rect(40, y, 248, 42)
            if self.sequence_selection == index:
                pygame.draw.rect(self.screen, theme.ACCENT_GLOW, row_rect, border_radius=16)
            self._multiline(step.label, self.fonts["small"], theme.INK if self.sequence_selection == index else theme.MUTED, row_rect.inflate(-8, -6))
            y += 48

    def _draw_move_list(self) -> None:
        y = 90
        for number, white, black in self.current_move_rows:
            row = pygame.Rect(1178, y, 220, 42)
            pygame.draw.rect(self.screen, theme.PANEL_MUTED, row, border_radius=16)
            if (number, white, black) == self.current_move_rows[-1]:
                pygame.draw.rect(self.screen, theme.ACCENT_GLOW, row, border_radius=16)
            self._text(f"{number}.", self.fonts["small"], theme.MUTED, (1192, y + 11))
            self._text(white, self.fonts["small"], theme.INK, (1232, y + 11))
            self._text(black, self.fonts["small"], theme.INK, (1310, y + 11))
            y += 50

    def _draw_player_banner(self, side: str, name: str, clock_text: str, rect: pygame.Rect, active: bool) -> None:
        self._text(side, self.fonts["eyebrow"], theme.MUTED, (rect.x + 22, rect.y + 14))
        self._text(name, self.fonts["subtitle"], theme.INK, (rect.x + 22, rect.y + 36))
        clock_rect = pygame.Rect(rect.right - 138, rect.y + 20, 112, 46)
        pygame.draw.rect(self.screen, theme.ACCENT_GLOW if active else theme.PANEL_MUTED, clock_rect, border_radius=18)
        pygame.draw.rect(self.screen, theme.ACCENT if active else "#d2bea8", clock_rect, 2, border_radius=18)
        clock_surface = self.fonts["clock"].render(clock_text, True, theme.INK if active else theme.MUTED)
        self.screen.blit(clock_surface, clock_surface.get_rect(center=clock_rect.center))

    def _draw_overlay(self, title: str, body: str) -> None:
        overlay_rect = pygame.Rect(468, 354, 530, 220)
        overlay_surface = pygame.Surface(overlay_rect.size, pygame.SRCALPHA)
        overlay_surface.fill((44, 35, 29, 228))
        self.screen.blit(overlay_surface, overlay_rect.topleft)
        pygame.draw.rect(self.screen, "#f1decb", overlay_rect, 2, border_radius=22)
        kicker = self.fonts["eyebrow"].render("Game Finished", True, "#ead6c3")
        self.screen.blit(kicker, kicker.get_rect(center=(overlay_rect.centerx, overlay_rect.y + 42)))
        title_surface = self.fonts["overlay"].render(title, True, "#fff7ef")
        self.screen.blit(title_surface, title_surface.get_rect(center=(overlay_rect.centerx, overlay_rect.y + 96)))
        self._multiline(body, self.fonts["body"], "#fff7ef", pygame.Rect(overlay_rect.x + 40, overlay_rect.y + 132, 450, 54), center=True)

    def _rounded_panel(self, rect: pygame.Rect) -> None:
        pygame.draw.rect(self.screen, theme.PANEL_BG, rect, border_radius=28)
        pygame.draw.rect(self.screen, "#d8c0a7", rect, 1, border_radius=28)

    def _text(self, value: str, font: pygame.font.Font, color: str, position: tuple[int, int]) -> None:
        self.screen.blit(font.render(value, True, color), position)

    def _multiline(
        self,
        text: str,
        font: pygame.font.Font,
        color: str,
        rect: pygame.Rect,
        *,
        center: bool = False,
    ) -> None:
        words = text.split()
        lines: list[str] = []
        current = ""
        for word in words:
            attempt = word if not current else f"{current} {word}"
            if font.size(attempt)[0] <= rect.width:
                current = attempt
            else:
                lines.append(current)
                current = word
        if current:
            lines.append(current)

        y = rect.y
        for line in lines:
            rendered = font.render(line, True, color)
            line_rect = rendered.get_rect()
            line_rect.midtop = (rect.centerx, y) if center else (rect.x + rendered.get_width() / 2, y)
            if not center:
                line_rect.x = rect.x
            self.screen.blit(rendered, line_rect)
            y += rendered.get_height() + 4


def main() -> None:
    ChessocApp().run()
