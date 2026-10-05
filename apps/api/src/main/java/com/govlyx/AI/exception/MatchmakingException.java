package com.govlyx.AI.exception;

class MatchmakingException extends RuntimeException {
    public MatchmakingException(String message) {
        super(message);
    }

    public MatchmakingException(String message, Throwable cause) {
        super(message, cause);
    }
}
