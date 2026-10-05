package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.ActorProfile;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.ActorProfileRepo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ActorProfileServiceTest {

    @Mock
    private ActorProfileRepo actorProfileRepo;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @Mock
    private CivicPseudonymService civicPseudonymService;

    @InjectMocks
    private ActorProfileService actorProfileService;

    @Test
    void testCreateOrCopyFromUser_NewActorToken_MintsFreshPseudonymAndCopiesPreferences() {
        User user = User.builder()
                .id(1L)
                .username("acc_user12345")
                .profileImage("https://example.com/avatar.jpg")
                .bio("Dedicated to local community improvements.")
                .pincode("411001")
                .homeLatitude(new BigDecimal("18.52043000"))
                .homeLongitude(new BigDecimal("73.85674000"))
                .mutedWords("spam,clickbait,promo")
                .blockedActors("act_bad1,act_bad2")
                .profanityFilterLevel("MEDIUM")
                .copyrightStrikes(1)
                .isAdult(true)
                .theme("dark")
                .interfaceLanguage("mr")
                .preferredLanguage("hi")
                .autoTranslate(true)
                .build();

        String actorToken = "act_crypto_test_token_12345";

        when(actorProfileRepo.findByActorToken(actorToken)).thenReturn(Optional.empty());
        when(civicPseudonymService.generateUniquePseudonym()).thenReturn("WildDragon5670");
        when(actorProfileRepo.save(any(ActorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActorProfile profile = actorProfileService.createOrCopyFromUser(actorToken, user);

        assertNotNull(profile);
        assertEquals(actorToken, profile.getActorToken());
        assertEquals("WildDragon5670", profile.getUsername());
        assertEquals("WildDragon5670", profile.getDisplayName());
        assertEquals("https://example.com/avatar.jpg", profile.getProfileImage());
        assertEquals("Dedicated to local community improvements.", profile.getBio());
        assertEquals("411001", profile.getPincode());
        assertEquals(new BigDecimal("18.52043000"), profile.getHomeLatitude());
        assertEquals(new BigDecimal("73.85674000"), profile.getHomeLongitude());
        assertEquals("spam,clickbait,promo", profile.getMutedWords());
        assertEquals("act_bad1,act_bad2", profile.getBlockedActors());
        assertEquals("MEDIUM", profile.getProfanityFilterLevel());
        assertEquals(1, profile.getCopyrightStrikes());
        assertTrue(profile.getIsAdult());
        assertEquals("dark", profile.getTheme());
        assertEquals("mr", profile.getInterfaceLanguage());
        assertEquals("hi", profile.getPreferredLanguage());
        assertTrue(profile.getAutoTranslate());
        assertNotNull(profile.getCreatedAt());
        assertNotNull(profile.getUpdatedAt());

        verify(actorProfileRepo, times(1)).save(any(ActorProfile.class));
    }

    @Test
    void testCreateOrCopyFromUser_NewTokenCleanBreak_MintsNewPseudonymWithoutDeletingOldProfile() {
        String oldToken = "act_old_token_111";
        String newToken = "act_new_token_222";

        ActorProfile oldProfile = ActorProfile.builder()
                .actorToken(oldToken)
                .username("WildDragon5670")
                .createdAt(new Date(System.currentTimeMillis() - 100000))
                .build();

        User user = User.builder()
                .id(1L)
                .username("acc_user12345")
                .pincode("411001")
                .theme("light")
                .build();

        when(actorProfileRepo.findByActorToken(newToken)).thenReturn(Optional.empty());
        when(civicPseudonymService.generateUniquePseudonym()).thenReturn("SharpDolphin4874");
        when(actorProfileRepo.save(any(ActorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActorProfile updatedProfile = actorProfileService.createOrCopyFromUser(newToken, user);

        assertNotNull(updatedProfile);
        assertEquals(newToken, updatedProfile.getActorToken());
        assertEquals("SharpDolphin4874", updatedProfile.getUsername());
        assertEquals("SharpDolphin4874", updatedProfile.getDisplayName());
        assertEquals("411001", updatedProfile.getPincode());
        assertEquals("light", updatedProfile.getTheme());

        // Model 1 Clean Break: Old profile is NEVER deleted or correlated!
        verify(actorProfileRepo, never()).delete(any());
        verify(actorProfileRepo, times(1)).save(any(ActorProfile.class));
    }

    @Test
    void testCreateOrCopyFromUser_ExistingSameToken_UpdatesFieldsDirectly() {
        String token = "act_existing_token_999";

        ActorProfile existingProfile = ActorProfile.builder()
                .actorToken(token)
                .username("CitizenAlpha")
                .bio("Old bio")
                .build();

        User user = User.builder()
                .id(1L)
                .username("CitizenAlpha")
                .bio("New updated bio")
                .theme("dark")
                .build();

        when(actorProfileRepo.findByActorToken(token)).thenReturn(Optional.of(existingProfile));
        when(actorProfileRepo.save(any(ActorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActorProfile result = actorProfileService.createOrCopyFromUser(token, user);

        assertEquals("CitizenAlpha", result.getDisplayName());
        assertEquals("New updated bio", result.getBio());
        assertEquals("dark", result.getTheme());

        // Verify delete was NOT called since token is identical
        verify(actorProfileRepo, never()).delete(any());
        verify(actorProfileRepo, times(1)).save(existingProfile);
    }
}
