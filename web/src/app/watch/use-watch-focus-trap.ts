"use client";

import { useEffect, type RefObject } from 'react';

export function useWatchFocusTrap(expanded: boolean, panelRef: RefObject<HTMLElement | null>, onClose: () => void) {
  useEffect(() => {
    const panel = panelRef.current;
    if (!expanded || !panel) return;
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const previousOverflow = document.body.style.overflow;
    const siblings: { element: HTMLElement; inert: boolean }[] = [];
    let current: HTMLElement | null = panel;
    while (current?.parentElement) {
      for (const element of current.parentElement.children) {
        if (element !== current && element instanceof HTMLElement) {
          siblings.push({ element, inert: element.hasAttribute('inert') });
          element.setAttribute('inert', '');
        }
      }
      if (current.parentElement === document.body) break;
      current = current.parentElement;
    }
    document.body.style.overflow = 'hidden';

    function controls() {
      return Array.from(panel!.querySelectorAll<HTMLElement>('a[href], button:not(:disabled), [tabindex="0"]'))
        .filter((element) => element.getClientRects().length > 0 && !element.closest('[inert]'));
    }
    function keepFocus(event: FocusEvent) {
      if (!panel!.contains(event.target as Node)) (controls()[0] ?? panel!).focus();
    }
    function keyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        event.preventDefault();
        onClose();
      } else if (event.key === 'Tab') {
        const focusable = controls();
        const first = focusable[0] ?? panel!;
        const last = focusable.at(-1) ?? panel!;
        if (event.shiftKey && (document.activeElement === first || !panel!.contains(document.activeElement) || document.activeElement === panel)) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && (document.activeElement === last || !panel!.contains(document.activeElement) || document.activeElement === panel)) {
          event.preventDefault();
          first.focus();
        }
      }
    }
    (controls()[0] ?? panel).focus();
    document.addEventListener('keydown', keyDown);
    document.addEventListener('focusin', keepFocus);
    return () => {
      document.removeEventListener('keydown', keyDown);
      document.removeEventListener('focusin', keepFocus);
      for (const { element, inert } of siblings) {
        if (!inert) element.removeAttribute('inert');
      }
      document.body.style.overflow = previousOverflow;
      if (previousFocus?.isConnected) previousFocus.focus();
      else if (panel.isConnected) panel.focus();
    };
  }, [expanded, onClose, panelRef]);
}
