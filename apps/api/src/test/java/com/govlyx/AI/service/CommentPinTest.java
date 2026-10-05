package com.govlyx.AI.service;

import com.govlyx.AI.dto.CommentDto;
import com.govlyx.AI.model.Comment;
import com.govlyx.AI.model.Post;
import com.govlyx.AI.model.SocialPost;
import com.govlyx.AI.model.User;
import com.govlyx.AI.payload.PostUtility;
import com.govlyx.AI.payload.SocialPostUtility;
import com.govlyx.AI.repository.CommentRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CommentPinTest {

    @Mock
    private CommentRepo commentRepo;

    @InjectMocks
    private CommentService commentService;

    private User author;
    private Post post;
    private SocialPost socialPost;
    private Comment postComment;
    private Comment socialComment;

    @BeforeEach
    void setUp() {
        author = new User();
        author.setId(2L);
        author.setUsername("acc_2_75f31315");

        post = new Post();
        post.setId(100L);
        post.setUser(author);
        post.setActorToken("post_actor_token_123");

        socialPost = new SocialPost();
        socialPost.setId(200L);
        socialPost.setUser(author);
        socialPost.setActorToken("social_post_actor_token_456");

        postComment = new Comment();
        postComment.setId(3714L);
        postComment.setPost(post);
        postComment.setIsPinned(false);

        socialComment = new Comment();
        socialComment.setId(3715L);
        socialComment.setSocialPost(socialPost);
        socialComment.setIsPinned(false);
    }

    @Test
    void isPostOwner_shouldReturnTrueWhenRelationalUserMatchesEvenIfActorTokenDiffers() {
        assertTrue(PostUtility.isPostOwner(post, author, "different_user_actor_token"));
        assertTrue(SocialPostUtility.isSocialPostOwner(socialPost, author, "different_user_actor_token"));
    }

    @Test
    void isPostOwner_shouldReturnTrueWhenActorTokenMatchesForAnonymousPost() {
        Post anonPost = new Post();
        anonPost.setId(101L);
        anonPost.setActorToken("matching_token");
        anonPost.setUser(null);

        assertTrue(PostUtility.isPostOwner(anonPost, author, "matching_token"));
        assertFalse(PostUtility.isPostOwner(anonPost, author, "other_token"));
    }

    @Test
    void pinComment_shouldTogglePinStateForPostAuthor() {
        when(commentRepo.findById(3714L)).thenReturn(Optional.of(postComment));
        when(commentRepo.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CommentDto result = commentService.pinComment(3714L, author);

        assertNotNull(result);
        assertTrue(postComment.getIsPinned());
        verify(commentRepo, times(1)).save(postComment);
    }

    @Test
    void pinComment_shouldTogglePinStateForSocialPostAuthor() {
        when(commentRepo.findById(3715L)).thenReturn(Optional.of(socialComment));
        when(commentRepo.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CommentDto result = commentService.pinComment(3715L, author);

        assertNotNull(result);
        assertTrue(socialComment.getIsPinned());
        verify(commentRepo, times(1)).save(socialComment);
    }

    @Test
    void pinComment_shouldThrowSecurityExceptionForUnauthorizedUser() {
        User nonAuthor = new User();
        nonAuthor.setId(99L);
        nonAuthor.setUsername("stranger");

        when(commentRepo.findById(3714L)).thenReturn(Optional.of(postComment));

        assertThrows(RuntimeException.class, () -> commentService.pinComment(3714L, nonAuthor));
    }
}
