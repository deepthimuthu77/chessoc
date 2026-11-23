package com.chessoc.server.chat;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ChatPolicy {
    public static final int MAX_LENGTH = 500;
    private static final Pattern GRAPHEME = Pattern.compile("\\X");
    private static final String SEPARATOR = "[\\p{Z}\\p{P}\\p{S}_]*";
    private static final Pattern PROFANITY = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(?:" + List.of(
                    "fuck", "fucks", "fucker", "fuckers", "fucking", "fucked",
                    "motherfucker", "motherfuckers", "motherfucking",
                    "shit", "shits", "shitty", "shithead", "shitheads", "bullshit",
                    "bitch", "bitches", "bitching", "cunt", "cunts",
                    "dick", "dicks", "dickhead", "dickheads", "asshole", "assholes",
                    "bastard", "bastards", "wanker", "wankers")
                    .stream().map(word -> word.chars().mapToObj(c -> switch (c) {
                                case 'a' -> "[a4@]+"; case 'b' -> "[b8]+"; case 'e' -> "[e3]+";
                                case 'i' -> "[i1!]+"; case 'o' -> "[o0]+"; case 's' -> "[s5$]+";
                                case 't' -> "[t7]+"; default -> Pattern.quote(String.valueOf((char) c)) + "+";
                            })
                            .collect(Collectors.joining(SEPARATOR)))
                    .collect(Collectors.joining("|")) + ")(?![\\p{L}\\p{N}])");

    private ChatPolicy() {}

    public static String prepare(String text) {
        if (text == null) return "";
        StringBuilder clean = new StringBuilder();
        text.codePoints().forEach(cp -> {
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                if (!clean.isEmpty() && clean.charAt(clean.length() - 1) != ' ') clean.append(' ');
            } else if (!Character.isISOControl(cp) && !(cp >= 0xD800 && cp <= 0xDFFF)
                    && (Character.getType(cp) != Character.FORMAT || cp == 0x200C || cp == 0x200D)) {
                clean.appendCodePoint(cp);
            }
        });
        String body = clean.toString().strip();
        var graphemes = GRAPHEME.matcher(body);
        int end = 0;
        while (graphemes.find() && graphemes.end() <= MAX_LENGTH) end = graphemes.end();
        return mask(body.substring(0, end).strip());
    }

    private static String mask(String text) {
        StringBuilder normalized = new StringBuilder();
        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();
        for (int offset = 0; offset < text.length();) {
            int cp = text.codePointAt(offset);
            int end = offset + Character.charCount(cp);
            String folded = Normalizer.normalize(new String(Character.toChars(cp)), Normalizer.Form.NFKD)
                    .toLowerCase(Locale.ROOT);
            for (int index = 0; index < folded.length(); index++) {
                char c = folded.charAt(index);
                int type = Character.getType(c);
                if (type == Character.FORMAT || type == Character.NON_SPACING_MARK
                        || type == Character.COMBINING_SPACING_MARK || type == Character.ENCLOSING_MARK) continue;
                normalized.append(c);
                starts.add(offset);
                ends.add(end);
            }
            offset = end;
        }
        char[] masked = text.toCharArray();
        var matches = PROFANITY.matcher(normalized);
        while (matches.find()) {
            int start = starts.get(matches.start());
            int end = ends.get(matches.end() - 1);
            java.util.Arrays.fill(masked, start, end, '*');
        }
        return new String(masked);
    }
}
