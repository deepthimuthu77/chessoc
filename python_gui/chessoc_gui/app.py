from __future__ import annotations

import pygame

from . import theme
from .board_view import BoardView
from .controls import Button
from .fen import parse_board

WINDOW_SIZE = (1460, 940)
FPS = 60


class ChessocApp:
    def __init__(self) -> None:
        pygame.init()
        pygame.display.set_caption("Chessoc")
        self.screen = pygame.display.set_mode(WINDOW_SIZE)
        self.clock = pygame.time.Clock()
        self.fonts = self._build_fonts()
        self.board_view = BoardView(origin=(368, 142), square_size=78)
        self.running = True

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
            for event in pygame.event.get():
                if event.type == pygame.QUIT:
                    self.running = False

            self.screen.fill(theme.SHELL_BG)
            self._draw_panels()
            self.board_view.draw(
                self.screen,
                self.fonts,
                self._default_board(),
            )
            pygame.display.flip()
            self.clock.tick(FPS)
        pygame.quit()

    def _default_board(self) -> dict[str, str]:
        return parse_board("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")

    def _draw_panels(self) -> None:
        self._rounded_panel(pygame.Rect(24, 24, 280, 892))
        self._rounded_panel(pygame.Rect(1156, 24, 280, 892))
        self._rounded_panel(pygame.Rect(344, 24, 780, 92))
        self._rounded_panel(pygame.Rect(344, 798, 780, 92))

        self._text("Chessoc", self.fonts["eyebrow"], theme.MUTED, (44, 44))
        self._text("Chess Client", self.fonts["title"], theme.INK, (44, 66))
        self._multiline(
            "Board renderer awaiting a server projection.",
            self.fonts["body"],
            theme.MUTED,
            pygame.Rect(44, 132, 240, 90),
        )

        self._draw_player_banner("Black", "Not connected", "--:--", pygame.Rect(344, 24, 780, 92), False)
        self._draw_player_banner("White", "Not connected", "--:--", pygame.Rect(344, 798, 780, 92), False)

        self._text("Moves", self.fonts["subtitle"], theme.INK, (1178, 44))
        self._text("No state received.", self.fonts["small"], theme.MUTED, (1178, 90))

    def _draw_player_banner(self, side: str, name: str, clock_text: str, rect: pygame.Rect, active: bool) -> None:
        self._text(side, self.fonts["eyebrow"], theme.MUTED, (rect.x + 22, rect.y + 14))
        self._text(name, self.fonts["subtitle"], theme.INK, (rect.x + 22, rect.y + 36))
        clock_rect = pygame.Rect(rect.right - 138, rect.y + 20, 112, 46)
        pygame.draw.rect(self.screen, theme.ACCENT_GLOW if active else theme.PANEL_MUTED, clock_rect, border_radius=18)
        pygame.draw.rect(self.screen, theme.ACCENT if active else "#d2bea8", clock_rect, 2, border_radius=18)
        clock_surface = self.fonts["clock"].render(clock_text, True, theme.INK if active else theme.MUTED)
        self.screen.blit(clock_surface, clock_surface.get_rect(center=clock_rect.center))

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