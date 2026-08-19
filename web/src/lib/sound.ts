// A short synthesized move tick via Web Audio — no asset files, no dependency.

let ctx: AudioContext | null = null;

export function playMoveSound(): void {
  if (typeof window === 'undefined') {
    return;
  }
  try {
    const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    ctx = ctx ?? new Ctor();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = 'sine';
    osc.frequency.value = 300;
    const t = ctx.currentTime;
    gain.gain.setValueAtTime(0.0001, t);
    gain.gain.exponentialRampToValueAtTime(0.14, t + 0.008);
    gain.gain.exponentialRampToValueAtTime(0.0001, t + 0.12);
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.start(t);
    osc.stop(t + 0.13);
  } catch {
    // audio unavailable — ignore
  }
}
