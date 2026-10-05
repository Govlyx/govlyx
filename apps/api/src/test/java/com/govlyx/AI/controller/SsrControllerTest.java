package com.Govlyx.AI.controller;

import com.Govlyx.AI.model.Post;
import com.Govlyx.AI.model.SocialPost;
import com.Govlyx.AI.repository.CommunityRepo;
import com.Govlyx.AI.repository.PincodeLookupRepo;
import com.Govlyx.AI.repository.PostRepo;
import com.Govlyx.AI.repository.SocialPostRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SsrControllerTest {

    @Mock
    private PostRepo postRepo;

    @Mock
    private SocialPostRepo socialPostRepo;

    @Mock
    private CommunityRepo communityRepo;

    @Mock
    private PincodeLookupRepo pincodeLookupRepo;

    @InjectMocks
    private SsrController ssrController;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ssrController, "baseUrl", "https://govlyx.com");
    }

    @Test
    void ssrIssue_ShouldReturnHtml_WhenPostExists() {
        Post mockPost = new Post();
        mockPost.setId(1L);
        mockPost.setContent("Road is broken in my area");
        mockPost.setLikeCount(10);
        mockPost.setCommentCount(5);

        when(postRepo.findById(1L)).thenReturn(Optional.of(mockPost));

        ResponseEntity<String> response = ssrController.ssrIssue(1L);

        assertEquals(200, response.getStatusCodeValue());
        String body = response.getBody();
        assertTrue(body.contains("Road is broken in my area"));
        assertTrue(body.contains("Civic Issue | Govlyx"));
    }

    @Test
    void ssrIssue_ShouldReturn404_WhenPostNotFound() {
        when(postRepo.findById(99L)).thenReturn(Optional.empty());

        ResponseEntity<String> response = ssrController.ssrIssue(99L);

        assertEquals(404, response.getStatusCodeValue());
    }

    @Test
    void ssrQa_ShouldReturnHtml_WhenSocialPostExists() {
        SocialPost mockSocialPost = new SocialPost();
        mockSocialPost.setId(2L);
        mockSocialPost.setContent("Any good places to eat around here?");
        mockSocialPost.setPincode("110001");
        mockSocialPost.setLikeCount(20);
        mockSocialPost.setCommentCount(15);

        when(socialPostRepo.findById(2L)).thenReturn(Optional.of(mockSocialPost));

        ResponseEntity<String> response = ssrController.ssrQa(2L);

        assertEquals(200, response.getStatusCodeValue());
        String body = response.getBody();
        assertTrue(body.contains("Any good places to eat around here?"));
        assertTrue(body.contains("Neighbourhood Q&A | Govlyx"));
    }

    @Test
    void ssrQa_ShouldReturn404_WhenSocialPostNotFound() {
        when(socialPostRepo.findById(99L)).thenReturn(Optional.empty());

        ResponseEntity<String> response = ssrController.ssrQa(99L);

        assertEquals(404, response.getStatusCodeValue());
    }

    @Test
    void ssrCommunity_ShouldReturnHtml() {
        com.Govlyx.AI.model.Community mockCommunity = new com.Govlyx.AI.model.Community();
        mockCommunity.setName("delhi-ncr");
        mockCommunity.setDescription("Delhi NCR Community");
        when(communityRepo.findBySlug("delhi-ncr")).thenReturn(Optional.of(mockCommunity));

        ResponseEntity<String> response = ssrController.ssrCommunity("delhi-ncr");

        assertEquals(200, response.getStatusCodeValue());
        String body = response.getBody();
        assertTrue(body.contains("delhi-ncr Community | Govlyx"));
    }
}
