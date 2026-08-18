package com.chessoc.server.account;

import com.chessoc.server.session.AuthException;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Registration and password authentication. Identity is the account id. */
public final class AccountService {

    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,20}");

    private final AccountRepository accounts;
    private final Supplier<String> ids;
    private final LongSupplier clock;

    public AccountService(AccountRepository accounts) {
        this(accounts, () -> UUID.randomUUID().toString(), System::currentTimeMillis);
    }

    public AccountService(AccountRepository accounts, Supplier<String> ids, LongSupplier clock) {
        this.accounts = accounts;
        this.ids = ids;
        this.clock = clock;
    }

    public Account register(String username, String email, String password) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new AuthException("username must be 3-20 letters, digits or underscore");
        }
        if (email == null || !EMAIL.matcher(email).matches()) {
            throw new AuthException("invalid email");
        }
        if (password == null || password.length() < 6) {
            throw new AuthException("password must be at least 6 characters");
        }
        Account account = new Account(ids.get(), username, email,
                PasswordHasher.hash(password), clock.getAsLong());
        accounts.create(account); // repository enforces uniqueness
        return account;
    }

    public Account authenticate(String email, String password) {
        Account account = accounts.findByEmail(email)
                .orElseThrow(() -> new AuthException("no account for that email"));
        if (!PasswordHasher.verify(password, account.passwordHash())) {
            throw new AuthException("wrong password");
        }
        return account;
    }
}
