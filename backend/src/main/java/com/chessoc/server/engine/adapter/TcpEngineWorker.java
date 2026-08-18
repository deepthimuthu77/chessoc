package com.chessoc.server.engine.adapter;

import com.chessoc.server.engine.EngineErrorCode;
import com.chessoc.server.engine.EngineException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * An engine worker over a TCP connection to an engine service. Same line contract as the process
 * worker: write the request lines, read the JSON reply. A read past the budget is an internal fault.
 */
public final class TcpEngineWorker implements EngineWorker {

    private final Socket socket;
    private final BufferedReader in;
    private final BufferedWriter out;

    public TcpEngineWorker(String host, int port, Duration connectTimeout) {
        try {
            this.socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), (int) connectTimeout.toMillis());
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new EngineException(EngineErrorCode.INTERNAL_ERROR, "connect failed: " + e.getMessage());
        }
    }

    @Override
    public synchronized String request(String payload, Duration timeout) {
        try {
            socket.setSoTimeout((int) Math.max(1, timeout.toMillis()));
            out.write(payload);
            out.write('\n');
            out.flush();
            String line;
            while ((line = in.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("{")) {
                    return trimmed;
                }
            }
            throw new EngineException(EngineErrorCode.INTERNAL_ERROR, "engine closed the connection");
        } catch (SocketTimeoutException e) {
            throw new EngineException(EngineErrorCode.INTERNAL_ERROR, "timeout after " + timeout.toMillis() + "ms");
        } catch (IOException e) {
            throw new EngineException(EngineErrorCode.INTERNAL_ERROR, "io error: " + e.getMessage());
        }
    }

    @Override
    public boolean isReady() {
        return socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // best effort
        }
    }
}
