package com.chessoc.server.engine.adapter;

import com.chessoc.server.engine.EngineErrorCode;
import com.chessoc.server.engine.EngineException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An engine worker backed by a child process over stdin/stdout. A read that outlives the budget kills
 * the process — a hung engine cannot self-report, so timeout is the Adapter's internal-error signal.
 */
public final class ProcessEngineWorker implements EngineWorker {

    private final Process process;
    private final BufferedWriter in;
    private final BufferedReader out;
    private final ExecutorService reader;

    public ProcessEngineWorker(List<String> command) {
        try {
            this.process = new ProcessBuilder(command).redirectErrorStream(false).start();
        } catch (IOException e) {
            throw new EngineException(EngineErrorCode.INTERNAL_ERROR, "spawn failed: " + e.getMessage());
        }
        this.in = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.out = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        this.reader = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "engine-reader");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public synchronized String request(String payload, Duration timeout) {
        if (!process.isAlive()) {
            throw new EngineException(EngineErrorCode.INTERNAL_ERROR, "worker not alive");
        }
        try {
            in.write(payload);
            in.write('\n');
            in.flush();
        } catch (IOException e) {
            throw new EngineException(EngineErrorCode.INTERNAL_ERROR, "write failed: " + e.getMessage());
        }

        Future<String> pending = reader.submit(this::readReplyLine);
        try {
            String line = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (line == null) {
                throw new EngineException(EngineErrorCode.INTERNAL_ERROR, "worker closed its output");
            }
            return line;
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw new EngineException(EngineErrorCode.INTERNAL_ERROR, "timeout after " + timeout.toMillis() + "ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EngineException(EngineErrorCode.INTERNAL_ERROR, "interrupted");
        } catch (ExecutionException e) {
            throw new EngineException(EngineErrorCode.INTERNAL_ERROR, "read failed: " + e.getCause());
        }
    }

    /** Skip UCI chatter; the reply we want is the JSON line. */
    private String readReplyLine() throws IOException {
        String line;
        while ((line = out.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.startsWith("{")) {
                return trimmed;
            }
        }
        return null;
    }

    @Override
    public boolean isReady() {
        return process.isAlive();
    }

    @Override
    public void close() {
        reader.shutdownNow();
        process.destroyForcibly();
    }
}
