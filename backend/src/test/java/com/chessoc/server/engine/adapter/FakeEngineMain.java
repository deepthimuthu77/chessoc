package com.chessoc.server.engine.adapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Minimal engine stand-in: reads request lines, emits a canned JSON reply per query. Subprocess only. */
public final class FakeEngineMain {

    private FakeEngineMain() {
    }

    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("apply")) {
                emit("{\"legal\":true,\"fen\":"
                        + "\"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1\","
                        + "\"terminal\":\"none\",\"inCheck\":false,\"sideToMove\":\"b\",\"capturedDropType\":null}");
            } else if (line.startsWith("legalmoves")) {
                emit("{\"moves\":[\"e2e4\",\"d2d4\"]}");
            } else if (line.startsWith("canmate")) {
                emit("{\"canMate\":true}");
            } else if (line.startsWith("status")) {
                emit("{\"terminal\":\"none\",\"inCheck\":false,\"sideToMove\":\"w\"}");
            }
        }
    }

    private static void emit(String json) {
        System.out.println(json);
        System.out.flush();
    }
}
