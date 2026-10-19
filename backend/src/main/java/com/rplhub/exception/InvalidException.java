package com.rplhub.exception;

/** 400 with a user-readable message. */
public class InvalidException extends RuntimeException {

    public InvalidException(String message) {
        super(message);
    }
}
