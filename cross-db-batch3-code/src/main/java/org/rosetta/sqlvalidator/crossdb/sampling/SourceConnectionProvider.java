package org.rosetta.sqlvalidator.crossdb.sampling;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface SourceConnectionProvider {
    Connection openConnection() throws SQLException;
}
