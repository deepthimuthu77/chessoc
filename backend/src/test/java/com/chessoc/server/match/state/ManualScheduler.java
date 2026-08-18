package com.chessoc.server.match.state;

import java.util.ArrayList;
import java.util.List;

/** Test scheduler: captures scheduled tasks and fires them on demand. */
final class ManualScheduler implements Scheduler {

    private final List<Runnable> tasks = new ArrayList<>();

    @Override
    public Cancellable schedule(Runnable task, long delayMs) {
        tasks.add(task);
        return () -> tasks.remove(task);
    }

    void runAll() {
        for (Runnable task : new ArrayList<>(tasks)) {
            task.run();
        }
    }
}
