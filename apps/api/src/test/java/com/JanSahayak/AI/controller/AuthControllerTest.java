package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.dto.RegisterRequest;
import com.JanSahayak.AI.dto.request.VaultBlobRequest;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.model.Role;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.RoleRepo;
import com.JanSahayak.AI.repository.UserRepo;
import com.JanSahayak.AI.security.IdentityBlindService;
import com.JanSahayak.AI.service.ActorProfileService;
import com.JanSahayak.AI.service.EmailService;
import com.JanSahayak.AI.service.RateLimitingService;
import com.JanSahayak.AI.service.UserService;
import com.JanSahayak.AI.service.PincodeValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private RateLimitingService rateLimitingService;

    @Mock
    private RoleRepo roleRepository;

    @Mock
    private UserRepo userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PincodeValidationService pincodeValidationService;

    @Mock
    private EmailService emailService;

    @Mock
    private IdentityBlindService identityBlindService;

    @Mock
    private ActorProfileService actorProfileService;

    @InjectMocks
    private AuthController authController;

    @Test
    void testRegisterCitizen_Success_SessionTokenGenerated() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");
        request.setPincode("110001");
        request.setIsAdult(true);

        when(rateLimitingService.isRegistrationBlocked(anyString())).thenReturn(false);
        when(userService.existsByEmail(anyString())).thenReturn(false);
        
        when(pincodeValidationService.isValidIndianPincode(anyString())).thenReturn(true);

        Role role = new Role();
        role.setName("ROLE_USER");
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(role));

        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");

        when(userService.saveUser(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            return savedUser;
        });

        ResponseEntity<ApiResponse<String>> response = authController.registerUser(request);

        assertEquals(200, response.getStatusCode().value(), "Registration should return 200 OK");
        
        verify(userService, times(1)).saveUser(argThat(user -> 
            user.getSessionToken() != null && !user.getSessionToken().isEmpty()
        ));
    }

    @Test
    void testSaveVaultBlob_CopiesAllFieldsFromUserToActorProfile() {
        User currentUser = User.builder()
                .id(42L)
                .username("CitizenAlpha")
                .pincode("411001")
                .bio("Dedicated civic contributor")
                .theme("dark")
                .seedBlindSalt("temporary_seed_salt")
                .build();

        VaultBlobRequest request = VaultBlobRequest.builder()
                .vaultBlob("encrypted_vault_ciphertext_data")
                .vaultSalt("salt_99999")
                .actorToken("act_new_actor_token_42")
                .build();

        ResponseEntity<ApiResponse<String>> response = authController.saveVaultBlob(request, currentUser);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("encrypted_vault_ciphertext_data", currentUser.getVaultBlob());
        assertEquals("salt_99999", currentUser.getVaultSalt());
        assertNull(currentUser.getSeedBlindSalt(), "Seed blind salt should be purged after vault blob encryption");

        verify(userRepository, times(1)).save(currentUser);
        verify(actorProfileService, times(1)).createOrCopyFromUser("act_new_actor_token_42", currentUser);
    }

    @Test
    void testInitVaultReset_ClearsVaultAndGeneratesFreshSeedSalt() {
        User currentUser = User.builder()
                .id(42L)
                .username("CitizenAlpha")
                .vaultBlob("old_encrypted_blob")
                .vaultSalt("old_vault_salt")
                .build();

        when(identityBlindService.generateActorSalt()).thenReturn("fresh_new_seed_salt_123");

        ResponseEntity<ApiResponse<Map<String, String>>> response = authController.initVaultReset(currentUser);

        assertEquals(200, response.getStatusCode().value());
        assertNull(currentUser.getVaultBlob(), "Old vault blob must be invalidated on reset");
        assertNull(currentUser.getVaultSalt(), "Old vault salt must be invalidated on reset");
        assertEquals("fresh_new_seed_salt_123", currentUser.getSeedBlindSalt());
        assertNotNull(response.getBody().getData());
        assertEquals("fresh_new_seed_salt_123", response.getBody().getData().get("seedBlindSalt"));

        verify(userRepository, times(1)).save(currentUser);
    }
}
