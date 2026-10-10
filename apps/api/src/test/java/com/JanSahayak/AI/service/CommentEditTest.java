package com.JanSahayak.AI.service;

import com.JanSahayak.AI.dto.CommentDto;
import com.JanSahayak.AI.dto.CommentUpdateDto;
import com.JanSahayak.AI.model.Comment;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.CommentRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CommentEditTest {

    @Mock
    private CommentRepo commentRepo;
    
    @Mock
    private ContentValidationService contentValidationService;

    @InjectMocks
    private CommentService commentService;

    private Comment comment;
    private User user;
    private CommentUpdateDto updateDto;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(10L);
        user.setUsername("testuser");

        Post post = new Post();
        post.setId(100L);
        post.setStatus(PostStatus.ACTIVE);

        comment = new Comment();
        comment.setId(1L);
        comment.setText("Original Text");
        comment.setUser(user);
        comment.setPost(post);
        // Do not set updatedAt initially

        updateDto = new CommentUpdateDto();
        updateDto.setText("Updated Text");
    }

    @Test
    void updateComment_shouldUpdateTextAndSetUpdatedAt() {
        when(commentRepo.findById(1L)).thenReturn(Optional.of(comment));
        when(contentValidationService.sanitizeAndValidateContent("Updated Text")).thenReturn("Updated Text");
        when(commentRepo.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CommentDto result = commentService.updateComment(1L, updateDto, user);

        assertNotNull(result);
        assertEquals("Updated Text", comment.getText());
        assertNotNull(comment.getUpdatedAt(), "updatedAt should be set during text edit");
        
        verify(commentRepo, times(1)).save(comment);
    }
}
