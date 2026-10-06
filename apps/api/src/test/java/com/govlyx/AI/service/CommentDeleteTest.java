package com.govlyx.AI.service;

import com.govlyx.AI.model.Comment;
import com.govlyx.AI.model.Community;
import com.govlyx.AI.model.Post;
import com.govlyx.AI.model.Role;
import com.govlyx.AI.model.SocialPost;
import com.govlyx.AI.model.User;
import com.govlyx.AI.repository.CommentInteractionRepository;
import com.govlyx.AI.repository.CommentRepo;
import com.govlyx.AI.repository.PostRepo;
import com.govlyx.AI.repository.SocialPostRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CommentDeleteTest {

    @Mock
    private CommentRepo commentRepository;

    @Mock
    private PostRepo postRepository;

    @Mock
    private SocialPostRepo socialPostRepository;

    @Mock
    private CommentInteractionRepository commentInteractionRepository;

    @Mock
    private CommunityService communityService;

    @InjectMocks
    private CommentService commentService;

    private User postAuthor;
    private User commentAuthor;
    private User stranger;
    private User admin;
    private User mod;

    private Post post;
    private SocialPost socialPost;
    private Community community;
    private Comment postComment;
    private Comment socialComment;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(commentService, "communityService", communityService);

        postAuthor = new User();
        postAuthor.setId(10L);
        postAuthor.setUsername("post_author");

        commentAuthor = new User();
        commentAuthor.setId(20L);
        commentAuthor.setUsername("comment_author");

        stranger = new User();
        stranger.setId(30L);
        stranger.setUsername("random_user");

        admin = new User();
        admin.setId(40L);
        admin.setUsername("admin_user");
        Role adminRole = new Role();
        adminRole.setName("ROLE_ADMIN");
        admin.setRole(adminRole);

        mod = new User();
        mod.setId(50L);
        mod.setUsername("mod_user");

        post = new Post();
        post.setId(100L);
        post.setUser(postAuthor);
        post.setCommentCount(1);

        community = new Community();
        community.setId(555L);

        socialPost = new SocialPost();
        socialPost.setId(200L);
        socialPost.setUser(postAuthor);
        socialPost.setCommunity(community);
        socialPost.setCommentCount(1);

        postComment = new Comment();
        postComment.setId(1001L);
        postComment.setUser(commentAuthor);
        postComment.setAuthorUsername(commentAuthor.getUsername());
        postComment.setPost(post);

        socialComment = new Comment();
        socialComment.setId(1002L);
        socialComment.setUser(commentAuthor);
        socialComment.setAuthorUsername(commentAuthor.getUsername());
        socialComment.setSocialPost(socialPost);
    }

    @Test
    void deleteComment_commentOwnerCanDelete() {
        when(commentRepository.findById(1001L)).thenReturn(Optional.of(postComment));
        when(commentRepository.findDescendantCommentIds(1001L)).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> commentService.deleteComment(1001L, commentAuthor));
        verify(commentRepository).delete(postComment);
    }

    @Test
    void deleteComment_postAuthorCanDeleteCommentOnTheirPost() {
        when(commentRepository.findById(1001L)).thenReturn(Optional.of(postComment));
        when(commentRepository.findDescendantCommentIds(1001L)).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> commentService.deleteComment(1001L, postAuthor));
        verify(commentRepository).delete(postComment);
        verify(postRepository).save(post);
    }

    @Test
    void deleteComment_socialPostAuthorCanDeleteCommentOnTheirSocialPost() {
        when(commentRepository.findById(1002L)).thenReturn(Optional.of(socialComment));
        when(commentRepository.findDescendantCommentIds(1002L)).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> commentService.deleteComment(1002L, postAuthor));
        verify(commentRepository).delete(socialComment);
        verify(socialPostRepository).save(socialPost);
    }

    @Test
    void deleteComment_communityModCanDeleteCommentOnCommunityPost() {
        when(commentRepository.findById(1002L)).thenReturn(Optional.of(socialComment));
        when(communityService.isModeratorOrAbove(555L, 50L)).thenReturn(true);
        when(commentRepository.findDescendantCommentIds(1002L)).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> commentService.deleteComment(1002L, mod));
        verify(commentRepository).delete(socialComment);
    }

    @Test
    void deleteComment_adminCanDeleteAnyComment() {
        when(commentRepository.findById(1001L)).thenReturn(Optional.of(postComment));
        when(commentRepository.findDescendantCommentIds(1001L)).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> commentService.deleteComment(1001L, admin));
        verify(commentRepository).delete(postComment);
    }

    @Test
    void deleteComment_unauthorizedUserCannotDelete() {
        when(commentRepository.findById(1001L)).thenReturn(Optional.of(postComment));

        assertThrows(SecurityException.class, () -> commentService.deleteComment(1001L, stranger));
        verify(commentRepository, never()).delete(any());
    }
}
