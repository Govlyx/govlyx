package com.JanSahayak.AI.service;

import com.JanSahayak.AI.dto.CommentCreateDto;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.model.Community;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.CommentRepo;
import com.JanSahayak.AI.repository.PostLikeRepo;
import com.JanSahayak.AI.repository.SocialPostRepo;
import com.JanSahayak.AI.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CommunityArchiveInteractionGuardTest {

    @Mock
    private SocialPostRepo socialPostRepository;

    @Mock
    private CommentRepo commentRepo;

    @Mock
    private PostLikeRepo postLikeRepository;
    
    @Mock
    private ContentValidationService contentValidationService;

    @InjectMocks
    private PostInteractionService postInteractionService;

    @InjectMocks
    private CommentService commentService;

    private User user;
    private SocialPost activeCommunityPost;
    private SocialPost archivedCommunityPost;
    private SocialPost deletedCommunityPost;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("test_user");

        activeCommunityPost = new SocialPost();
        activeCommunityPost.setId(100L);
        activeCommunityPost.setStatus(PostStatus.ACTIVE);
        activeCommunityPost.setCommunityStatus(Community.CommunityStatus.ACTIVE.name());
        Community activeCommunity = new Community();
        activeCommunity.setId(10L);
        activeCommunityPost.setCommunity(activeCommunity);
        activeCommunityPost.setAllowComments(true);
        activeCommunityPost.setUser(user);

        archivedCommunityPost = new SocialPost();
        archivedCommunityPost.setId(101L);
        archivedCommunityPost.setStatus(PostStatus.ACTIVE);
        archivedCommunityPost.setCommunityStatus(Community.CommunityStatus.ARCHIVED.name());
        Community archivedCommunity = new Community();
        archivedCommunity.setId(10L);
        archivedCommunityPost.setCommunity(archivedCommunity);
        archivedCommunityPost.setAllowComments(true);
        archivedCommunityPost.setUser(user);

        deletedCommunityPost = new SocialPost();
        deletedCommunityPost.setId(102L);
        deletedCommunityPost.setStatus(PostStatus.ACTIVE);
        deletedCommunityPost.setCommunityStatus(Community.CommunityStatus.DELETED.name());
        Community deletedCommunity = new Community();
        deletedCommunity.setId(10L);
        deletedCommunityPost.setCommunity(deletedCommunity);
        deletedCommunityPost.setAllowComments(true);
        deletedCommunityPost.setUser(user);
    }

    @Test
    void testLikePost_ThrowsExceptionIfCommunityArchived() {
        when(socialPostRepository.findById(101L)).thenReturn(Optional.of(archivedCommunityPost));
        
        assertThrows(ValidationException.class, () -> {
            postInteractionService.likeSocialPostById(101L, user);
        });
        
        verify(socialPostRepository, never()).incrementLikeCount(anyLong());
    }

    @Test
    void testLikePost_ThrowsExceptionIfCommunityDeleted() {
        when(socialPostRepository.findById(102L)).thenReturn(Optional.of(deletedCommunityPost));
        
        assertThrows(ValidationException.class, () -> {
            postInteractionService.likeSocialPostById(102L, user);
        });
        
        verify(socialPostRepository, never()).incrementLikeCount(anyLong());
    }

    @Test
    void testCommentPost_ThrowsExceptionIfCommunityArchived() {
        CommentCreateDto dto = new CommentCreateDto();
        dto.setText("Hello world");
        
        when(socialPostRepository.findById(101L)).thenReturn(Optional.of(archivedCommunityPost));
        
        assertThrows(com.JanSahayak.AI.exception.ServiceException.class, () -> {
            commentService.createCommentOnSocialPostById(101L, dto, user);
        });
        
        verify(commentRepo, never()).save(any());
    }

    @Test
    void testCommentPost_ThrowsExceptionIfCommunityDeleted() {
        CommentCreateDto dto = new CommentCreateDto();
        dto.setText("Hello world");
        
        when(socialPostRepository.findById(102L)).thenReturn(Optional.of(deletedCommunityPost));
        
        assertThrows(com.JanSahayak.AI.exception.ServiceException.class, () -> {
            commentService.createCommentOnSocialPostById(102L, dto, user);
        });
        
        verify(commentRepo, never()).save(any());
    }
}
