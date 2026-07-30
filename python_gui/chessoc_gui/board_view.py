from __future__ import annotations

import pygame

from . import theme


FILES = "abcdefgh"
PIECES = {
    "K": "♔", "Q": "♕", "R": "♖", "B": "♗", "N": "♘", "P": "♙",
    "k": "♚", "q": "♛", "r": "♜", "b": "♝", "n": "♞", "p": "♟",
}


class BoardView:
    def __init__(self, origin: tuple[int, int], square_size: int) -> None:
        self.origin = origin
        self.square_size = square_size
        self.board_pixels = square_size * 8
        self.frame_rect = pygame.Rect(origin[0] - 24, origin[1] - 24, self.board_pixels + 48, self.board_pixels + 48)
        self.board_rect = pygame.Rect(origin[0], origin[1], self.board_pixels, self.board_pixels)

    def draw(
        self,
        surface: pygame.Surface,
        fonts: dict[str, pygame.font.Font],
        board: dict[str, str],
        *,
        last_move: tuple[str, str] | None = None,
        selected_square: str | None = None,
        target_squares: set[str] | None = None,
        capture_square: str | None = None,
        hidden_squares: set[str] | None = None,
        pulse: bool = False,
    ) -> None:
        hidden = hidden_squares or set()
        targets = target_squares or set()

        pygame.draw.rect(surface, theme.PANEL_BG, self.frame_rect, border_radius=30)
        border_color = theme.DANGER if pulse else "#d8c0a7"
        border_width = 4 if pulse else 1
        pygame.draw.rect(surface, border_color, self.frame_rect, border_width, border_radius=30)

        for rank in range(8, 0, -1):
            for file_index in range(8):
                square = f"{FILES[file_index]}{rank}"
                rect = self.square_rect(square)
                color = theme.BOARD_LIGHT if (file_index + rank) % 2 == 0 else theme.BOARD_DARK
                pygame.draw.rect(surface, color, rect)

                if last_move and square in last_move:
                    highlight = pygame.Surface(rect.size, pygame.SRCALPHA)
                    highlight.fill((248, 223, 157, 96))
                    surface.blit(highlight, rect.topleft)

                if square == selected_square:
                    pygame.draw.rect(surface, theme.ACCENT, rect.inflate(-8, -8), 3, border_radius=12)

                if square in targets:
                    pygame.draw.circle(surface, theme.HIGHLIGHT, rect.center, 14)

                if square == capture_square:
                    pygame.draw.circle(surface, theme.DANGER, rect.center, 28, 4)

                coord = fonts["coord"].render(square, True, "#2f241e")
                surface.blit(coord, (rect.x + 7, rect.y + 7))

                piece = board.get(square)
                if piece and square not in hidden:
                    glyph = fonts["piece"].render(PIECES[piece], True, "#fffaf5" if piece.islower() else "#231913")
                    surface.blit(glyph, glyph.get_rect(center=rect.center))

    def draw_ghost(
        self,
        surface: pygame.Surface,
        piece: str,
        position: tuple[float, float],
        piece_font: pygame.font.Font,
    ) -> None:
        glyph = piece_font.render(PIECES[piece], True, "#fffaf5" if piece.islower() else "#231913")
        shadow = piece_font.render(PIECES[piece], True, (0, 0, 0))
        shadow_rect = shadow.get_rect(center=(position[0] + 2, position[1] + 8))
        shadow_surface = pygame.Surface(shadow.get_size(), pygame.SRCALPHA)
        shadow_surface.blit(shadow, (0, 0))
        shadow_surface.set_alpha(50)
        surface.blit(shadow_surface, shadow_rect)
        surface.blit(glyph, glyph.get_rect(center=position))

    def square_at(self, position: tuple[int, int]) -> str | None:
        if not self.board_rect.collidepoint(position):
            return None
        relative_x = position[0] - self.origin[0]
        relative_y = position[1] - self.origin[1]
        file_index = relative_x // self.square_size
        rank = 8 - (relative_y // self.square_size)
        return f"{FILES[file_index]}{rank}"

    def square_rect(self, square: str) -> pygame.Rect:
        file_index = FILES.index(square[0])
        rank = int(square[1])
        return pygame.Rect(
            self.origin[0] + file_index * self.square_size,
            self.origin[1] + (8 - rank) * self.square_size,
            self.square_size,
            self.square_size,
        )

    def center_of(self, square: str) -> tuple[float, float]:
        return self.square_rect(square).center
