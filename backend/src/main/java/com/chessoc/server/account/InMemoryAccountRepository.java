package com.chessoc.server.account;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory accounts for dev and tests. */
public final class InMemoryAccountRepository implements AccountRepository {

    private final Map<String, Account> byId = new ConcurrentHashMap<>();

    @Override
    public void create(Account account) {
        byId.values().forEach(a -> {
            if (a.email().equalsIgnoreCase(account.email())) {
                throw new IllegalStateException("email taken");
            }
            if (a.username().equalsIgnoreCase(account.username())) {
                throw new IllegalStateException("username taken");
            }
        });
        byId.put(account.id(), account);
    }

    @Override
    public Optional<Account> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Account> findByEmail(String email) {
        return byId.values().stream().filter(a -> a.email().equalsIgnoreCase(email)).findFirst();
    }

    @Override
    public Optional<Account> findByUsername(String username) {
        return byId.values().stream().filter(a -> a.username().equalsIgnoreCase(username)).findFirst();
    }

    @Override
    public void updatePassword(String id, String passwordHash) {
        byId.computeIfPresent(id, (k, a) ->
                new Account(a.id(), a.username(), a.email(), passwordHash, a.createdAtEpochMs()));
    }

    @Override
    public void delete(String id) {
        byId.remove(id);
    }

    @Override
    public List<Account> byIds(List<String> ids) {
        List<Account> out = new ArrayList<>();
        for (String id : ids) {
            Account a = byId.get(id);
            if (a != null) {
                out.add(a);
            }
        }
        return out;
    }
}
