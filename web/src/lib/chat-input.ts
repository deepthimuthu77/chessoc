export const CHAT_MAX_LENGTH = 500;
const graphemes = new Intl.Segmenter(undefined, { granularity: 'grapheme' });

export function limitChatText(value: string): string {
  let end = 0;
  for (const part of graphemes.segment(value)) {
    const next = part.index + part.segment.length;
    if (next > CHAT_MAX_LENGTH) break;
    end = next;
  }
  return value.slice(0, end);
}

export function insertChatEmoji(value: string, emoji: string, start: number, end: number) {
  const next = value.slice(0, start) + emoji + value.slice(end);
  if (next.length > CHAT_MAX_LENGTH) return null;
  return { value: next, cursor: start + emoji.length };
}
