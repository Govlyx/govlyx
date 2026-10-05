package com.Govlyx.AI.controller;

import com.Govlyx.AI.dto.GoogleAuthRequest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GoogleAuthRequest DTO logic.
 * No Spring context needed — tests pure method logic.
 */
class GoogleAuthRequestTest {

    @Test
    void isRegistrationRequest_returnsFalse_whenOnlyTokenProvided() {
        GoogleAuthRequest req = new GoogleAuthRequest();
        req.setToken("google-token");
        assertThat(req.isRegistrationRequest()).isFalse();
    }

    @Test
    void isRegistrationRequest_returnsFalse_whenPincodeNullButConsentsTrue() {
        GoogleAuthRequest req = new GoogleAuthRequest();
        req.setToken("google-token");
        req.setIsAdult(true);
        req.setAcceptedPolicy(true);
        // pincode is null
        assertThat(req.isRegistrationRequest()).isFalse();
    }

    @Test
    void isRegistrationRequest_returnsFalse_whenPincodeBlank() {
        GoogleAuthRequest req = new GoogleAuthRequest();
        req.setToken("google-token");
        req.setPincode("   ");
        req.setIsAdult(true);
        req.setAcceptedPolicy(true);
        assertThat(req.isRegistrationRequest()).isFalse();
    }

    @Test
    void isRegistrationRequest_returnsFalse_whenIsAdultFalse() {
        GoogleAuthRequest req = new GoogleAuthRequest();
        req.setToken("google-token");
        req.setPincode("110001");
        req.setIsAdult(false);
        req.setAcceptedPolicy(true);
        assertThat(req.isRegistrationRequest()).isFalse();
    }

    @Test
    void isRegistrationRequest_returnsFalse_whenPolicyNotAccepted() {
        GoogleAuthRequest req = new GoogleAuthRequest();
        req.setToken("google-token");
        req.setPincode("110001");
        req.setIsAdult(true);
        req.setAcceptedPolicy(false);
        assertThat(req.isRegistrationRequest()).isFalse();
    }

    @Test
    void isRegistrationRequest_returnsTrue_whenAllPhase2FieldsValid() {
        GoogleAuthRequest req = new GoogleAuthRequest();
        req.setToken("google-token");
        req.setPincode("110001");
        req.setIsAdult(true);
        req.setAcceptedPolicy(true);
        assertThat(req.isRegistrationRequest()).isTrue();
    }

    @Test
    void isRegistrationRequest_returnsFalse_whenIsAdultNull() {
        GoogleAuthRequest req = new GoogleAuthRequest();
        req.setToken("google-token");
        req.setPincode("110001");
        req.setIsAdult(null);
        req.setAcceptedPolicy(true);
        assertThat(req.isRegistrationRequest()).isFalse();
    }

    @Test
    void isRegistrationRequest_returnsFalse_whenAcceptedPolicyNull() {
        GoogleAuthRequest req = new GoogleAuthRequest();
        req.setToken("google-token");
        req.setPincode("110001");
        req.setIsAdult(true);
        req.setAcceptedPolicy(null);
        assertThat(req.isRegistrationRequest()).isFalse();
    }
}
