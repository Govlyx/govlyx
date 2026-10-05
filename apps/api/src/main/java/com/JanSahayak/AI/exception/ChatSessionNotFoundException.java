package com.JanSahayak.AI.exception;

public class ChatSessionNotFoundException extends RuntimeException{
    public ChatSessionNotFoundException(String message) {
        super(message);
    }

    public ChatSessionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
