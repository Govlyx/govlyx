package com.Govlyx.AI.exception;

import com.Govlyx.AI.dto.PostResponse;
import lombok.Getter;

@Getter
public class DuplicatePostException extends RuntimeException {

    private final PostResponse duplicatePost;

    public DuplicatePostException(String message, PostResponse duplicatePost) {
        super(message);
        this.duplicatePost = duplicatePost;
    }
}
