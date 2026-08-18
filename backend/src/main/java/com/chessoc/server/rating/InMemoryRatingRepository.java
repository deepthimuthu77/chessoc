package com.chessoc.server.rating;

import com.chessoc.server.matchmaking.Rating;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory ratings for dev and tests. */
public final class InMemoryRatingRepository implements RatingRepository {

    private final Map<String, RatingRow> rows = new ConcurrentHashMap<>();

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
}
