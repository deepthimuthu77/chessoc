package com.chessoc.server.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Thin JDBC helper shared by the Postgres repositories. A pool is a drop-in improvement. */
public final class Db {

    private final String url;
    private final String user;
    private final String password;

    public Db(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public void exec(String ddl) {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute(ddl);
        } catch (SQLException e) {
            throw new IllegalStateException("DDL failed: " + ddl, e);
        }
    }
}
