'use client';

import { useEffect, useState, type ReactNode } from 'react';
import { MotionConfig, useReducedMotion } from 'framer-motion';
import { getSettings } from '@/lib/settings';

export function Experience({ children }: { children: ReactNode }) {
  const [reduced, setReduced] = useState(false);
  const systemReduced = useReducedMotion();
  useEffect(() => {
    const update = () => {
      const value = getSettings().reducedMotion;
      setReduced(value);
      document.documentElement.dataset.reducedMotion = String(value);
    };
    update();
    window.addEventListener('storage', update);
    window.addEventListener('chessoc-settings-changed', update);
    return () => {
      window.removeEventListener('storage', update);
      window.removeEventListener('chessoc-settings-changed', update);
    };
  }, []);
  return <MotionConfig reducedMotion={reduced ? 'always' : 'user'} transition={{ duration: reduced || systemReduced ? 0 : 0.22, ease: [0.22, 1, 0.36, 1] }}>{children}</MotionConfig>;
}
