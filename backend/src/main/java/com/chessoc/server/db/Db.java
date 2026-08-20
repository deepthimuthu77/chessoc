package com.chessoc.server.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Thin JDBC helper shared by the Postgres repositories, backed by a HikariCP connection pool. */
public final class Db implements AutoCloseable {

    private final HikariDataSource pool;

    public Db(String url, String user, String password) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername(user);
        cfg.setPassword(password);
        cfg.setMaximumPoolSize(10);
        cfg.setMinimumIdle(2);
        cfg.setPoolName("chessoc-db");
        this.pool = new HikariDataSource(cfg);
    }

    public Connection connect() throws SQLException {
        return pool.getConnection();
    }

    public void exec(String ddl) {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute(ddl);
        } catch (SQLException e) {
            throw new IllegalStateException("DDL failed: " + ddl, e);
        }
    }

    @Override
    public void close() {
        pool.close();
    }
}
