package com.rplhub.model;

import java.util.Map;

/** One competency line of a source file, normalised to the common column names (see FileParser.COLUMNS). */
public record RawRow(int rowNumber, Map<String, String> values) {
    public String get(String column) {
        String v = values.get(column);
        return v == null ? "" : v.trim();
    }
}
