package com.rplhub.qualification;

/** A validation finding for an import. row is the source row number, null when it concerns the whole qualification. */
record Issue(Integer rowNumber, String qualificationCode, String severity, String field, String message) {
    static Issue error(Integer row, String code, String field, String message) {
        return new Issue(row, code, "ERROR", field, message);
    }
    static Issue warning(Integer row, String code, String field, String message) {
        return new Issue(row, code, "WARNING", field, message);
    }
    static Issue info(String code, String message) {
        return new Issue(null, code, "INFO", null, message);
    }
}
