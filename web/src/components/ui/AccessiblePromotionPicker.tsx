'use client';

import { useRef, type ComponentProps } from 'react';
import { PromotionPicker } from './PromotionPicker';
import { useDialogFocus } from './useDialogFocus';

export function AccessiblePromotionPicker(props: ComponentProps<typeof PromotionPicker>) {
  const ref = useRef<HTMLDivElement>(null);
  useDialogFocus(ref, props.open, props.onCancel);
  if (!props.open) return null;
  return (
    <div ref={ref} role="dialog" aria-modal="true" aria-label="Choose promotion piece" tabIndex={-1} className="fixed inset-0 z-50">
      <PromotionPicker {...props} />
    </div>
  );
}
