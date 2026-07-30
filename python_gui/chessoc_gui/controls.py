from __future__ import annotations

from dataclasses import dataclass

import pygame

from . import theme


@dataclass
class Button:
    label: str
    rect: pygame.Rect

    def draw(self, surface: pygame.Surface, font: pygame.font.Font, hovered: bool) -> None:
        fill = theme.ACCENT_GLOW if hovered else theme.PANEL_MUTED
        pygame.draw.rect(surface, fill, self.rect, border_radius=18)
        pygame.draw.rect(surface, theme.ACCENT if hovered else "#d2bea8", self.rect, 2, border_radius=18)
        text = font.render(self.label, True, theme.INK)
        surface.blit(text, text.get_rect(center=self.rect.center))

    def contains(self, position: tuple[int, int]) -> bool:
        return self.rect.collidepoint(position)
