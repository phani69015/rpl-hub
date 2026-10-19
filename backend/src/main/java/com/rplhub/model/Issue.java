package com.rplhub.model;

/** A validation finding for an import. row is the source row number, null when it concerns the whole qualification. */
public record Issue(Integer rowNumber, String qualificationCode, String severity, String field, String message) {
    public static Issue error(Integer row, String code, String field, String message) {
        return new Issue(row, code, "ERROR", field, message);
    }
    public static Issue warning(Integer row, String code, String field, String message) {
        return new Issue(row, code, "WARNING", field, message);
    }
    public static Issue info(String code, String message) {
        return new Issue(null, code, "INFO", null, message);
    }
}
