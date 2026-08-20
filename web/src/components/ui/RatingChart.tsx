"use client";

import type { RatingPoint } from '@/lib/api';

// Inline SVG line chart — no chart lib. viewBox units; scales to the container width.
const W = 640;
const H = 200;
const PAD = { top: 16, right: 16, bottom: 16, left: 40 };

export function RatingChart({ points }: { points: RatingPoint[] }) {
  if (points.length < 2) {
    return (
      <div className="flex h-40 items-center justify-center rounded-2xl border border-zinc-800 bg-zinc-900 text-sm italic text-zinc-600">
        Not enough rated games yet.
      </div>
    );
  }

  const ratings = points.map((p) => p.rating);
  const lo = Math.min(...ratings);
  const hi = Math.max(...ratings);
  const span = Math.max(1, hi - lo);
  const innerW = W - PAD.left - PAD.right;
  const innerH = H - PAD.top - PAD.bottom;

  const x = (i: number) => PAD.left + (points.length === 1 ? 0 : (i / (points.length - 1)) * innerW);
  const y = (r: number) => PAD.top + innerH - ((r - lo) / span) * innerH;

  const line = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(p.rating).toFixed(1)}`).join(' ');
  const area = `${line} L${x(points.length - 1).toFixed(1)},${(PAD.top + innerH).toFixed(1)} `
    + `L${x(0).toFixed(1)},${(PAD.top + innerH).toFixed(1)} Z`;
  const last = points[points.length - 1].rating;

  return (
    <svg viewBox={`0 0 ${W} ${H}`} className="w-full" role="img" aria-label="Rating over time">
      <defs>
        <linearGradient id="ratingFill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#3b82f6" stopOpacity="0.35" />
          <stop offset="100%" stopColor="#3b82f6" stopOpacity="0" />
        </linearGradient>
      </defs>

      {[hi, Math.round((hi + lo) / 2), lo].map((r) => (
        <g key={r}>
          <line x1={PAD.left} y1={y(r)} x2={W - PAD.right} y2={y(r)} stroke="#27272a" strokeWidth="1" />
          <text x={PAD.left - 6} y={y(r) + 4} textAnchor="end" fontSize="11" fill="#71717a">{r}</text>
        </g>
      ))}

      <path d={area} fill="url(#ratingFill)" />
      <path d={line} fill="none" stroke="#3b82f6" strokeWidth="2.5" strokeLinejoin="round" strokeLinecap="round" />
      <circle cx={x(points.length - 1)} cy={y(last)} r="4" fill="#3b82f6" />
    </svg>
  );
}
