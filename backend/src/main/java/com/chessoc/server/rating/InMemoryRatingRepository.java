package com.chessoc.server.rating;

import com.chessoc.server.matchmaking.Rating;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory ratings for dev and tests. */
public final class InMemoryRatingRepository implements RatingRepository {

    private final Map<String, RatingRow> rows = new ConcurrentHashMap<>();
    private final Map<String, List<RatingHistoryPoint>> history = new ConcurrentHashMap<>();

    private static String key(String accountId, String variant, String speed) {
        return accountId + '|' + variant + '|' + speed;
    }

    @Override
    public RatingRow get(String accountId, String variant, String speed) {
        return rows.getOrDefault(key(accountId, variant, speed),
                new RatingRow(accountId, Rating.provisional(), 0));
    }

    @Override
    public void save(String accountId, String variant, String speed, RatingRow row) {
        rows.put(key(accountId, variant, speed), row);
    }

    @Override
    public List<RatingRow> top(String variant, String speed, int limit) {
        return rows.entrySet().stream()
                .filter(e -> e.getKey().endsWith('|' + variant + '|' + speed))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparingDouble((RatingRow r) -> r.rating().rating()).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<RatingRow> among(List<String> accountIds, String variant, String speed) {
        return accountIds.stream()
                .map(id -> get(id, variant, speed))
                .sorted(Comparator.comparingDouble((RatingRow r) -> r.rating().rating()).reversed())
                .toList();
    }

    @Override
    public List<PoolRating> forAccount(String accountId) {
        return rows.entrySet().stream()
                .filter(e -> e.getKey().startsWith(accountId + '|'))
                .map(e -> {
                    String[] parts = e.getKey().split("\\|");
                    RatingRow r = e.getValue();
                    return new PoolRating(parts[1], parts[2], (int) Math.round(r.rating().rating()), r.games());
                })
                .toList();
    }

    @Override
    public void reassign(String oldId, String newId) {
        for (String key : List.copyOf(rows.keySet())) {
            if (key.startsWith(oldId + '|')) {
                RatingRow r = rows.remove(key);
                String pool = key.substring(oldId.length()); // "|variant|speed"
                rows.put(newId + pool, new RatingRow(newId, r.rating(), r.games()));
            }
        }
        for (String key : List.copyOf(history.keySet())) {
            if (key.startsWith(oldId + '|')) {
                history.put(newId + key.substring(oldId.length()), history.remove(key));
            }
        }
    }

    @Override
    public void appendHistory(String accountId, String variant, String speed, int rating, long at) {
        history.computeIfAbsent(key(accountId, variant, speed), k -> new CopyOnWriteArrayList<>())
                .add(new RatingHistoryPoint(at, rating));
    }

    @Override
    public List<RatingHistoryPoint> history(String accountId, String variant, String speed, int limit) {
        List<RatingHistoryPoint> all = history.getOrDefault(key(accountId, variant, speed), List.of());
        int from = Math.max(0, all.size() - limit);
        return new ArrayList<>(all.subList(from, all.size()));
    }
}
