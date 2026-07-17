package org.rosetta.sqlvalidator.crossdb.identity;

import java.util.Locale;
import java.util.Optional;

/**
 * Generates a stable, human-readable key that does not depend on SQL text,
 * SQL hash, source line number or the current sqlId.
 */
public final class BaselineKeyFactory {

    private static final String DELIMITER = "|";
    private static final String EMPTY_OPTIONAL_COMPONENT = "-";

    public Optional<String> create(BaselineIdentity identity) {
        if (identity == null) {
            return Optional.empty();
        }

        String serviceName = required(identity.serviceName());
        String className = required(identity.className());
        String methodName = required(identity.methodName());
        String sourceType = required(identity.sourceType());

        if (serviceName == null || className == null
                || methodName == null || sourceType == null) {
            return Optional.empty();
        }

        return Optional.of(String.join(
                DELIMITER,
                serviceName,
                className,
                methodName,
                sourceType,
                optional(identity.sqlVariableName()),
                Integer.toString(identity.occurrenceIndex())
        ));
    }

    private String required(String value) {
        return value == null || value.isBlank() ? null : normalize(value);
    }

    private String optional(String value) {
        return value == null || value.isBlank()
                ? EMPTY_OPTIONAL_COMPONENT
                : normalize(value);
    }

    private String normalize(String value) {
        return value.trim()
                .replace("|", "%7C")
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
