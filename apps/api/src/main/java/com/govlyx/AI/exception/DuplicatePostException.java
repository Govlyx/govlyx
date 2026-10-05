package com.govlyx.AI.exception;

import com.govlyx.AI.dto.PostResponse;
import lombok.Getter;

@Getter
public class DuplicatePostException extends RuntimeException {

    private final PostResponse duplicatePost;

    public DuplicatePostException(String message, PostResponse duplicatePost) {
        super(message);
        this.duplicatePost = duplicatePost;
    }
}
