package com.rplhub.exception;

/** An uploaded qualification file is structurally unusable (unreadable, wrong type, missing columns). */
public class ImportFormatException extends InvalidException {

    public ImportFormatException(String message) {
        super(message);
    }
}
