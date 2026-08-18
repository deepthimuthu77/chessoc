package com.chessoc.server.engine.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Uci;
import com.chessoc.server.domain.Variant;
import com.chessoc.server.engine.ApplyResult;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Exercises the TCP worker path against an in-JVM engine socket. */
class TcpEngineWorkerTest {

    private static final Fen START =
            Fen.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

    private ServerSocket server;
    private Thread acceptor;

    @BeforeEach
    void startEngine() throws IOException {
        server = new ServerSocket(0);
        acceptor = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    OutputStream out = socket.getOutputStream();
                    String line;
                    while ((line = in.readLine()) != null) {
                        String reply = replyFor(line.trim());
                        if (reply != null) {
                            out.write((reply + "\n").getBytes(StandardCharsets.UTF_8));
                            out.flush();
                        }
                    }
                } catch (IOException ignored) {
                    return;
                }
            }
        });
        acceptor.setDaemon(true);
        acceptor.start();
    }

    @AfterEach
    void stopEngine() throws IOException {
        server.close();
    }

    private static String replyFor(String line) {
        if (line.startsWith("apply")) {
            return "{\"legal\":true,\"fen\":\"" + START.raw()
                    + "\",\"terminal\":\"none\",\"inCheck\":false,\"sideToMove\":\"b\"}";
        }
        if (line.startsWith("legalmoves")) {
            return "{\"moves\":[\"e2e4\",\"d2d4\"]}";
        }
        return null;
    }

    @Test
    void appliesMoveOverTcp() {
        int port = server.getLocalPort();
        try (EngineWorkerPool pool = new EngineWorkerPool(1,
                () -> new TcpEngineWorker("localhost", port, Duration.ofSeconds(2)))) {
            PooledEngineAdapter adapter =
                    new PooledEngineAdapter(pool, pool, Duration.ofSeconds(2), Duration.ofSeconds(2));

            ApplyResult result = adapter.apply(Variant.STANDARD, START, List.of(), Uci.of("e2e4"));
            assertThat(result.legal()).isTrue();

            assertThat(adapter.legalMoves(Variant.STANDARD, START, List.of()))
                    .contains(Uci.of("e2e4"), Uci.of("d2d4"));
        }
    }
}
