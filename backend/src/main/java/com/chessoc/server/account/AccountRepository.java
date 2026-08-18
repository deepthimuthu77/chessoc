package com.chessoc.server.account;

import java.util.List;
import java.util.Optional;

/** Persistence of accounts. */
public interface AccountRepository {

    void create(Account account);

    Optional<Account> findById(String id);

    Optional<Account> findByEmail(String email);

    Optional<Account> findByUsername(String username);

    /** Usernames for a set of ids, for display (leaderboard, friends). */
    List<Account> byIds(List<String> ids);
}
