package com.rplhub.exception;

/** 409, optionally carrying the current server state so a client can resolve the conflict. */
public class ConflictException extends RuntimeException {

    private final transient Object server;

    public ConflictException(String message) {
        this(message, null);
    }

    public ConflictException(String message, Object server) {
        super(message);
        this.server = server;
    }

    public Object server() {
        return server;
    }
}
