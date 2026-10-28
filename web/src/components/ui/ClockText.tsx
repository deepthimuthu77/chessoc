'use client';

import { memo, useEffect, useState } from 'react';
import type { ClockSample, Color } from '@/lib/protocol';
import { displayClocks } from '@/lib/view-model';

export interface ClockTextProps {
  sample?: ClockSample;
  receivedAt: number;
  color: Color;
  running: boolean;
}

export const ClockText = memo(function ClockText({ sample, receivedAt, color, running }: ClockTextProps) {
  const [, tick] = useState(0);
  useEffect(() => {
    if (!sample || !running || sample.activeSide !== color) return;
    const interval = window.setInterval(() => tick((value) => value + 1), 100);
    return () => window.clearInterval(interval);
  }, [sample, color, running]);

  const clock = sample ? displayClocks(sample, receivedAt, running)[color] : '--:--';
  return <span role="timer" aria-label={`${color} clock`} aria-live="off">{clock}</span>;
});
