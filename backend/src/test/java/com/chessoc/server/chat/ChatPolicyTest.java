package com.chessoc.server.chat;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class ChatPolicyTest {
    @Test
    void masksCaseLeetspeakSeparatorsRepeatedLettersAndUnicodeEvasions() {
        for (String input : new String[] {"fuck", "FUCK!", "f.u.c.k", "f u c k", "sh1t", "sh!t",
                "biiiitch", "f\u200Buck", "ｆｕｃｋ", "fúck", "you fucking asshole"}) {
            assertThat(ChatPolicy.prepare(input)).as(input).contains("*");
        }
        assertThat(ChatPolicy.prepare("Good game, SHIT! 😀")).isEqualTo("Good game, ****! 😀");
    }

    @Test
    void doesNotCensorSubstringsInsideOrdinaryWords() {
        String safe = "classical assessment Scunthorpe assignment beautiful good game";
        assertThat(ChatPolicy.prepare(safe)).isEqualTo(safe);
    }

    @Test
    void retainsEmojiSequencesAndOtherLanguages() {
        String text = "😀🔥👍🏽👨‍👩‍👧‍👦🇮🇳 — नमस्ते বাংলা";
        assertThat(ChatPolicy.prepare(text)).isEqualTo(text);
        for (String emoji : new String[] {"😀", "👍🏽", "👨‍👩‍👧‍👦", "🇮🇳"}) {
            assertThat(ChatPolicy.prepare("x".repeat(499) + emoji)).isEqualTo("x".repeat(499));
        }
        assertThat(ChatPolicy.prepare("x".repeat(498) + "😀tail")).isEqualTo("x".repeat(498) + "😀");
    }

    @Test
    void dropsEmptyMessagesControlsAndBidiSpoofingButTreatsHtmlAsPlainText() {
        assertThat(ChatPolicy.prepare(null)).isEmpty();
        assertThat(ChatPolicy.prepare(" \t\n\u200B ")).isEmpty();
        assertThat(ChatPolicy.prepare("hello\u202Eworld\u0007")).isEqualTo("helloworld");
        assertThat(ChatPolicy.prepare("hello\n  world")).isEqualTo("hello world");
        String html = "<img src=x onerror=alert(1)>";
        assertThat(ChatPolicy.prepare(html)).isEqualTo(html);
        assertThat(ChatPolicy.prepare("x".repeat(2048))).hasSize(500);
    }
}
