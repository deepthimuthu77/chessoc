'use client';

import { useEffect, useRef, type RefObject } from 'react';

const FOCUSABLE = 'button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])';

export function useDialogFocus(ref: RefObject<HTMLElement | null>, open: boolean, onDismiss?: () => void) {
  const dismissRef = useRef(onDismiss);
  useEffect(() => { dismissRef.current = onDismiss; }, [onDismiss]);

  useEffect(() => {
    const dialog = ref.current;
    if (!open || !dialog) return;
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const items = () => Array.from(dialog.querySelectorAll<HTMLElement>(FOCUSABLE)).filter((element) => element.getClientRects().length > 0);
    const focusFirst = () => (items()[0] ?? dialog).focus();
    focusFirst();

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && dismissRef.current) {
        event.preventDefault();
        event.stopPropagation();
        dismissRef.current();
      }
      if (event.key !== 'Tab') return;
      const elements = items();
      const first = elements[0];
      const last = elements[elements.length - 1];
      if (!first || !last) {
        event.preventDefault();
        dialog?.focus();
      } else if (event.shiftKey && (document.activeElement === first || !elements.includes(document.activeElement as HTMLElement))) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && (document.activeElement === last || !elements.includes(document.activeElement as HTMLElement))) {
        event.preventDefault();
        first.focus();
      }
    }
    function onFocus(event: FocusEvent) {
      if (!dialog?.contains(event.target as Node)) focusFirst();
    }
    document.addEventListener('keydown', onKeyDown, true);
    document.addEventListener('focusin', onFocus);
    return () => {
      document.removeEventListener('keydown', onKeyDown, true);
      document.removeEventListener('focusin', onFocus);
      if (previous?.isConnected) previous.focus();
    };
  }, [open, ref]);
}
