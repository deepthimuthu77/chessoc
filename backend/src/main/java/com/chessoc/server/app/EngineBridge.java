package com.chessoc.server.app;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Exposes a stdio engine binary over TCP: one engine process per connection, lines relayed both ways.
 * Lets a containerized server reach a native engine binary that can't run inside the Linux image.
 *
 * <pre>java -cp "lib/*" com.chessoc.server.app.EngineBridge 9200 /path/to/ChessEngine-all[.exe]</pre>
 */
public final class EngineBridge {

    private EngineBridge() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("usage: EngineBridge <port> <engine> [engineArgs...]");
            System.exit(2);
        }
        int port = Integer.parseInt(args[0]);
        List<String> command = List.of(args).subList(1, args.length);
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("engine bridge on " + port + " -> " + command);
            while (true) {
                Socket socket = server.accept();
                Thread worker = new Thread(() -> relay(socket, command), "engine-bridge");
                worker.setDaemon(true);
                worker.start();
            }
        }
    }

    private static void relay(Socket socket, List<String> command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(false).start();
            Process engine = process;

            Thread pump = new Thread(() -> {
                try (BufferedReader out = new BufferedReader(
                        new InputStreamReader(engine.getInputStream(), StandardCharsets.UTF_8))) {
                    OutputStream toClient = socket.getOutputStream();
                    String line;
                    while ((line = out.readLine()) != null) {
                        toClient.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                        toClient.flush();
                    }
                } catch (IOException ignored) {
                    // connection or process closed
                }
            }, "engine-bridge-out");
            pump.setDaemon(true);
            pump.start();

            BufferedReader fromClient = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter toEngine = new BufferedWriter(
                    new OutputStreamWriter(engine.getOutputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = fromClient.readLine()) != null) {
                toEngine.write(line);
                toEngine.write('\n');
                toEngine.flush();
            }
        } catch (IOException ignored) {
            // client gone
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
            try {
                socket.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
    }
}
