'use client';

import { useEffect, useState, type ReactNode } from 'react';
import { MotionConfig } from 'framer-motion';
import { getSettings } from '@/lib/settings';

export function Experience({ children }: { children: ReactNode }) {
  const [reduced, setReduced] = useState(false);
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
  return <MotionConfig reducedMotion={reduced ? 'always' : 'user'} transition={{ duration: 0.2, ease: [0.2, 0.7, 0.2, 1] }}>{children}</MotionConfig>;
}
