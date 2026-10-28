import { PIECE_POSITIONS } from './Piece';
import { fenToPieces, lastMoveSquares } from '@/lib/view-model';

const FILES = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'];

export function LiveBoardPreview({
  fen,
  lastMove,
  moveCount,
  label,
}: {
  fen: string;
  lastMove: string;
  moveCount: number;
  label: string;
}) {
  const pieces = fenToPieces(fen);
  const lastMoveSquaresValue = lastMoveSquares(lastMove ? [lastMove] : []);

  return (
    <div
      role="img"
      aria-label={`${label ? `Board ${label}, ` : ''}live chess position after ${moveCount} moves`}
      className="relative grid aspect-square w-full grid-cols-8 overflow-hidden border border-zinc-800"
    >
      {Array.from({ length: 64 }, (_, index) => {
        const row = Math.floor(index / 8);
        const col = index % 8;
        const square = `${FILES[col]}${8 - row}`;
        const highlighted = lastMoveSquaresValue
          && (lastMoveSquaresValue.from === square || lastMoveSquaresValue.to === square);
        return (
          <div
            key={square}
            className={`relative aspect-square ${(row + col) % 2 ? 'bg-[#4b7399]' : 'bg-[#eae9d2]'}`}
          >
            {highlighted && <span className="absolute inset-0 bg-yellow-300/35" />}
          </div>
        );
      })}
      <div className="pointer-events-none absolute inset-0">
        {pieces.map(({ char, square }) => {
          const position = PIECE_POSITIONS[char];
          if (!position) return null;
          const col = FILES.indexOf(square[0]);
          const row = 8 - Number(square[1]);
          return (
            <span
              key={`${char}-${square}`}
              className="absolute h-[12.5%] w-[12.5%] p-[2%] drop-shadow-md"
              style={{ left: `${col * 12.5}%`, top: `${row * 12.5}%` }}
            >
              <span
                className="block h-full w-full"
                style={{
                  backgroundImage: 'url(/Chess_Pieces_Sprite.svg)',
                  backgroundPosition: `${position.x} ${position.y}`,
                  backgroundRepeat: 'no-repeat',
                  backgroundSize: '600% 200%',
                }}
              />
            </span>
          );
        })}
      </div>
      {label && (
        <span className="absolute left-1.5 top-1.5 bg-zinc-950/80 px-1.5 py-0.5 text-[10px] font-bold text-zinc-200">
          {label}
        </span>
      )}
    </div>
  );
}
