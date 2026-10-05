package com.JanSahayak.AI.exception;

public class ChatEncryptionException extends RuntimeException{
    public ChatEncryptionException(String message) {
        super(message);
    }

    public ChatEncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
