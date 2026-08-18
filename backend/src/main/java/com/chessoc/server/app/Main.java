package com.chessoc.server.app;

import com.chessoc.server.engine.EngineAdapter;
import com.chessoc.server.engine.adapter.EngineWorker;
import com.chessoc.server.engine.adapter.EngineWorkerPool;
import com.chessoc.server.engine.adapter.PooledEngineAdapter;
import com.chessoc.server.engine.adapter.ProcessEngineWorker;
import com.chessoc.server.engine.adapter.TcpEngineWorker;
import com.chessoc.server.engine.adapter.UnavailableEngine;
import java.util.function.Supplier;

/** Entry point. Wires the engine from config (or a loud stand-in) and starts the server. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        ServerConfig config = ServerConfig.fromEnv();
        EngineAdapter engine = buildEngine(config);
        ServerApp app = new ServerApp(config, engine);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                app.stop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        app.start();
        System.out.println("chessoc-server up - http=" + app.httpPort() + " ws=" + app.wsPort()
                + " engine=" + engineMode(config)
                + " db=" + (config.dbUrl().isBlank() ? "memory" : "postgres"));
    }

    private static String engineMode(ServerConfig config) {
        if (config.enginePort() > 0) {
            return "tcp:" + config.engineHost() + ":" + config.enginePort();
        }
        return config.validatorCommand().isEmpty() ? "none" : "process";
    }

    private static EngineAdapter buildEngine(ServerConfig config) {
        Supplier<EngineWorker> validators;
        Supplier<EngineWorker> searchers;
        if (config.enginePort() > 0) {
            validators = () -> new TcpEngineWorker(config.engineHost(), config.enginePort(), config.validatorTimeout());
            searchers = validators;
        } else if (!config.validatorCommand().isEmpty()) {
            validators = () -> new ProcessEngineWorker(config.validatorCommand());
            searchers = config.searchCommand().isEmpty()
                    ? validators
                    : () -> new ProcessEngineWorker(config.searchCommand());
        } else {
            return new UnavailableEngine();
        }
        return new PooledEngineAdapter(
                new EngineWorkerPool(config.validatorWorkers(), validators),
                new EngineWorkerPool(config.searchWorkers(), searchers),
                config.validatorTimeout(), config.searchTimeout());
    }
}
