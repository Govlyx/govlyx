package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.PincodeLookup;
import com.JanSahayak.AI.repository.PincodeLookupRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class PincodeValidationService {

    @Autowired
    private PincodeLookupRepo pincodeLookupRepo;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Validates a pincode.
     * Returns true if valid, false if invalid.
     * Throws RuntimeException if API is down so caller can handle fallback.
     */
    public boolean isValidIndianPincode(String pincode) {
        // 1. Regex validation
        if (pincode == null || !pincode.matches("^[1-9][0-9]{5}$")) {
            return false;
        }

        // 2. Database validation
        if (pincodeLookupRepo.existsByPincodeAndIsActiveTrue(pincode)) {
            return true;
        }

        // 3. API validation
        String url = "https://api.postalpincode.in/pincode/" + pincode;
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode rootNode = objectMapper.readTree(response.getBody());
                if (rootNode.isArray() && rootNode.size() > 0) {
                    JsonNode resultNode = rootNode.get(0);
                    String status = resultNode.path("Status").asText();
                    
                    if ("Success".equalsIgnoreCase(status)) {
                        JsonNode postOffices = resultNode.path("PostOffice");
                        if (postOffices.isArray() && postOffices.size() > 0) {
                            JsonNode firstPO = postOffices.get(0);
                            
                            PincodeLookup lookup = new PincodeLookup();
                            lookup.setPincode(pincode);
                            lookup.setAreaName(firstPO.path("Name").asText());
                            lookup.setDistrict(firstPO.path("District").asText());
                            lookup.setState(firstPO.path("State").asText());
                            // Some PO nodes don't have city, map to District if missing
                            lookup.setCity(firstPO.has("Block") && !firstPO.path("Block").asText().equalsIgnoreCase("NA") 
                                ? firstPO.path("Block").asText() 
                                : firstPO.path("District").asText());
                                
                            pincodeLookupRepo.save(lookup);
                            return true;
                        }
                    } else if ("Error".equalsIgnoreCase(status)) {
                        return false;
                    }
                }
            }
        } catch (ResourceAccessException e) {
            log.error("Error accessing Postal API: {}", e.getMessage());
            throw new ApiUnavailableException("Postal API is temporarily down");
        } catch (Exception e) {
            log.error("Unexpected error validating pincode: {}", e.getMessage());
            throw new ApiUnavailableException("Unexpected error during pincode validation");
        }

        return false;
    }

    public static class ApiUnavailableException extends RuntimeException {
        public ApiUnavailableException(String message) {
            super(message);
        }
    }
}
