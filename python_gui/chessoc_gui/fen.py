from __future__ import annotations


FILES = "abcdefgh"


def parse_board(fen: str) -> dict[str, str]:
    board_part = fen.split(" ", 1)[0]
    board: dict[str, str] = {}
    ranks = board_part.split("/")
    for rank_index, rank_data in enumerate(ranks):
        file_index = 0
        for char in rank_data:
            if char.isdigit():
                file_index += int(char)
                continue
            square = f"{FILES[file_index]}{8 - rank_index}"
            board[square] = char
            file_index += 1
    return board
