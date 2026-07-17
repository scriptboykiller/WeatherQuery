package org.rosetta.sqlvalidator.crossdb.sampling;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DriverManagerSourceConnectionProvider implements SourceConnectionProvider {

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String schema;

    public DriverManagerSourceConnectionProvider(
            String jdbcUrl,
            String username,
            String password,
            String schema
    ) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.schema = schema;
    }

    @Override
    public Connection openConnection() throws SQLException {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalStateException("H2 JDBC URL is required");
        }

        Connection connection = DriverManager.getConnection(
                jdbcUrl,
                username == null ? "" : username,
                password == null ? "" : password);

        if (schema != null && !schema.isBlank()) {
            connection.setSchema(schema);
        }
        connection.setReadOnly(true);
        return connection;
    }
}
