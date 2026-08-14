package com.chessoc.server.engine;

/** A bounded search budget the server sets to keep AI think-time within clock rules. */
public record SearchBudget(Type type, long value) {

    public enum Type {
        MOVETIME,
        DEPTH,
        NODES
    }

    public static SearchBudget movetime(long ms) {
        return new SearchBudget(Type.MOVETIME, ms);
    }

    public static SearchBudget depth(long plies) {
        return new SearchBudget(Type.DEPTH, plies);
    }

    public static SearchBudget nodes(long nodes) {
        return new SearchBudget(Type.NODES, nodes);
    }

    /** UCI {@code go} argument, e.g. "movetime 1000". */
    public String uci() {
        return switch (type) {
            case MOVETIME -> "movetime " + value;
            case DEPTH -> "depth " + value;
            case NODES -> "nodes " + value;
        };
    }
}
