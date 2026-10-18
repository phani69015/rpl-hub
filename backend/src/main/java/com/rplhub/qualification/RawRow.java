package com.rplhub.qualification;

import java.util.Map;

/** One competency line of a source file, normalised to the common column names (see FileParser.COLUMNS). */
record RawRow(int rowNumber, Map<String, String> values) {
    String get(String column) {
        String v = values.get(column);
        return v == null ? "" : v.trim();
    }
}
