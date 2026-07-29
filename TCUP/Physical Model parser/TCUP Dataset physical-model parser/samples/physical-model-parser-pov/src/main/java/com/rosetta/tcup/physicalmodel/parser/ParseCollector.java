package com.rosetta.tcup.physicalmodel.parser;

import com.rosetta.tcup.physicalmodel.domain.PhysicalAttribute;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ParseCollector {
    private final Set<String> paths = new LinkedHashSet<>();
    private final List<String> warnings = new ArrayList<>();

    void addPath(String path) {
        if (path != null && !path.isBlank()) {
            paths.add(path);
        }
    }

    void warn(String warning) {
        if (!warnings.contains(warning)) {
            warnings.add(warning);
        }
    }

    List<PhysicalAttribute> attributes() {
        return paths.stream().map(PhysicalAttribute::new).toList();
    }

    List<String> warnings() {
        return List.copyOf(warnings);
    }
}
