package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.dto.PaginatedResponse;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.model.PincodeLookup;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.security.CurrentUser;
import com.JanSahayak.AI.service.PinCodeLookupService;
import com.JanSahayak.AI.service.PinCodeLookupService.PincodeStatsDto;
import com.JanSahayak.AI.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/pincode")
@RequiredArgsConstructor
@Slf4j
public class PinCodeLookupController {

    private final PinCodeLookupService pinCodeLookupService;
    private final UserService userService;

    // ===== Basic CRUD Operations =====

    @GetMapping("/{pincode}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PincodeLookup>> findByPincode(@PathVariable String pincode) {
        try {
            Optional<PincodeLookup> result = pinCodeLookupService.findByPincode(pincode);
            if (result.isPresent()) {
                return ResponseEntity.ok(ApiResponse.success("Pincode found successfully", result.get()));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Pincode not found"));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid pincode format", e.getMessage()));
        } catch (Exception e) {
            log.error("Error finding pincode: {}", pincode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", e.getMessage()));
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PincodeLookup>>> findAll(
            @RequestParam(required = false) String beforePincode,
            @RequestParam(defaultValue = "20") Integer limit) {
        try {
            PaginatedResponse<PincodeLookup> result = pinCodeLookupService.findAll(beforePincode, limit);
            return ResponseEntity.ok(ApiResponse.success("Pincodes retrieved successfully", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid parameters", e.getMessage()));
        } catch (Exception e) {
            log.error("Error finding all pincodes", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", e.getMessage()));
        }
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PincodeLookup>>> findActiveOnly(
            @RequestParam(required = false) String beforePincode,
            @RequestParam(defaultValue = "20") Integer limit) {
        try {
            PaginatedResponse<PincodeLookup> result = pinCodeLookupService.findActiveOnly(beforePincode, limit);
            return ResponseEntity.ok(ApiResponse.success("Active pincodes retrieved successfully", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid parameters", e.getMessage()));
        } catch (Exception e) {
            log.error("Error finding active pincodes", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", e.getMessage()));
        }
    }

    // ===== Search Operations =====

    @GetMapping("/search/area")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PincodeLookup>>> searchByAreaName(
            @RequestParam String areaName,
            @RequestParam(required = false) String beforePincode,
            @RequestParam(defaultValue = "20") Integer limit) {
        try {
            PaginatedResponse<PincodeLookup> result = pinCodeLookupService.searchByAreaName(areaName, beforePincode, limit);
            return ResponseEntity.ok(ApiResponse.success("Area search completed successfully", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid search parameters", e.getMessage()));
        } catch (Exception e) {
            log.error("Error searching by area name: {}", areaName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", e.getMessage()));
        }
    }

    @GetMapping("/search/city")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PincodeLookup>>> searchByCity(
            @RequestParam String city,
            @RequestParam(required = false) String beforePincode,
            @RequestParam(defaultValue = "20") Integer limit) {
        try {
            PaginatedResponse<PincodeLookup> result = pinCodeLookupService.searchByCity(city, beforePincode, limit);
            return ResponseEntity.ok(ApiResponse.success("City search completed successfully", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid search parameters", e.getMessage()));
        } catch (Exception e) {
            log.error("Error searching by city: {}", city, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", e.getMessage()));
        }
    }

    @GetMapping("/search/district")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PincodeLookup>>> findByDistrict(
            @RequestParam String district,
            @RequestParam(required = false) String beforePincode,
            @RequestParam(defaultValue = "20") Integer limit) {
        try {
            PaginatedResponse<PincodeLookup> result = pinCodeLookupService.findByDistrict(district, beforePincode, limit);
            return ResponseEntity.ok(ApiResponse.success("District search completed successfully", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid search parameters", e.getMessage()));
        } catch (Exception e) {
            log.error("Error searching by district: {}", district, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", e.getMessage()));
        }
    }

    @GetMapping("/search/state")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PincodeLookup>>> findByState(
            @RequestParam String state,
            @RequestParam(required = false) String beforePincode,
            @RequestParam(defaultValue = "20") Integer limit) {
        try {
            PaginatedResponse<PincodeLookup> result = pinCodeLookupService.findByState(state, beforePincode, limit);
            return ResponseEntity.ok(ApiResponse.success("State search completed successfully", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid search parameters", e.getMessage()));
        } catch (Exception e) {
            log.error("Error searching by state: {}", state, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", e.getMessage()));
        }
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PincodeLookup>>> searchGeneral(
            @RequestParam String query,
            @RequestParam(required = false) String beforePincode,
            @RequestParam(defaultValue = "20") Integer limit) {
        try {
            PaginatedResponse<PincodeLookup> result = pinCodeLookupService.searchGeneral(query, beforePincode, limit);
            return ResponseEntity.ok(ApiResponse.success("General search completed successfully", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid search parameters", e.getMessage()));
        } catch (Exception e) {
            log.error("Error in general search: {}", query, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", e.getMessage()));
        }
    }

    // ===== Validation Operations =====

    @GetMapping("/{pincode}/validate")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<Boolean>> isValidPincode(@PathVariable String pincode) {
        try {
            boolean isValid = pinCodeLookupService.isValidPincode(pincode);
            return ResponseEntity.ok(ApiResponse.success("Pincode validation completed", isValid));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid pincode format", e.getMessage()));
        } catch (Exception e) {
            log.error("Error validating pincode: {}", pincode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", e.getMessage()));
        }
    }

    @GetMapping("/{pincode}/active")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<Boolean>> isPincodeActive(@PathVariable String pincode) {
        try {
            boolean isActive = pinCodeLookupService.isPincodeActive(pincode);
            return ResponseEntity.ok(ApiResponse.success("Pincode activity status retrieved", isActive));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid pincode format", e.getMessage()));
        } catch (Exception e) {
            log.error("Error checking pincode activity: {}", pincode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", e.getMessage()));
        }
    }

    // ===== Geographic Operations =====

    @GetMapping("/{pincode}/nearby")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PincodeLookup>>> findNearbyPincodes(
            @PathVariable String pincode,
            @RequestParam double radiusKm,
            @RequestParam(required = false) String beforePincode,
            @RequestParam(defaultValue = "20") Integer limit) {
        try {
            PaginatedResponse<PincodeLookup> result = pinCodeLookupService.findNearbyPincodes(pincode, radiusKm, beforePincode, limit);
            return ResponseEntity.ok(ApiResponse.success("Nearby pincodes retrieved successfully", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid parameters", e.getMessage()));
        } catch (Exception e) {
            log.error("Error finding nearby pincodes for: {}", pincode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", e.getMessage()));
        }
    }

    @GetMapping("/search/state-district")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PincodeLookup>>> findByStateAndDistrict(
            @RequestParam String state,
            @RequestParam String district,
            @RequestParam(required = false) String beforePincode,
            @RequestParam(defaultValue = "20") Integer limit) {
        try {
            PaginatedResponse<PincodeLookup> result = pinCodeLookupService.findByStateAndDistrict(state, district, beforePincode, limit);
            return ResponseEntity.ok(ApiResponse.success("State and district search completed successfully", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid search parameters", e.getMessage()));
        } catch (Exception e) {
            log.error("Error searching by state: {} and district: {}", state, district, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", e.getMessage()));
        }
    }

    // ===== Prefix-Based Operations =====

    @GetMapping("/prefix/state/{statePrefix}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PincodeLookup>>> findByStatePrefix(
            @PathVariable String statePrefix,
            @RequestParam(required = false) String beforePincode,
            @RequestParam(defaultValue = "20") Integer limit) {
        try {
            PaginatedResponse<PincodeLookup> result = pinCodeLookupService.findByStatePrefix(statePrefix, beforePincode, limit);
            return ResponseEntity.ok(ApiResponse.success("State prefix search completed successfully", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid state prefix", e.getMessage()));
        } catch (Exception e) {
            log.error("Error searching by state prefix: {}", statePrefix, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", e.getMessage()));
        }
    }

    @GetMapping("/prefix/district/{districtPrefix}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PincodeLookup>>> findByDistrictPrefix(
            @PathVariable String districtPrefix,
            @RequestParam(required = false) String beforePincode,
            @RequestParam(defaultValue = "20") Integer limit) {
        try {
            PaginatedResponse<PincodeLookup> result = pinCodeLookupService.findByDistrictPrefix(districtPrefix, beforePincode, limit);
            return ResponseEntity.ok(ApiResponse.success("District prefix search completed successfully", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid district prefix", e.getMessage()));
        } catch (Exception e) {
            log.error("Error searching by district prefix: {}", districtPrefix, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", e.getMessage()));
        }
    }

    @PostMapping("/prefix/search")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PincodeLookup>>> findByPincodePrefixes(
            @RequestBody List<String> prefixes,
            @RequestParam(required = false) String beforePincode,
            @RequestParam(defaultValue = "20") Integer limit) {
        try {
            PaginatedResponse<PincodeLookup> result = pinCodeLookupService.findByPincodePrefixes(prefixes, beforePincode, limit);
            return ResponseEntity.ok(ApiResponse.success("Prefix search completed successfully", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid prefixes", e.getMessage()));
        } catch (Exception e) {
            log.error("Error searching by prefixes", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", e.getMessage()));
        }
    }

    // ===== Statistics and Analytics (Admin Only) =====

    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PincodeStatsDto>> getStatistics() {
        try {
            PincodeStatsDto stats = pinCodeLookupService.getStatistics();
            return ResponseEntity.ok(ApiResponse.success("Statistics retrieved successfully", stats));
        } catch (Exception e) {
            log.error("Error retrieving pincode statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", e.getMessage()));
        }
    }

    @GetMapping("/states")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<List<String>>> getAllStates() {
        try {
            List<String> states = pinCodeLookupService.getAllStates();
            return ResponseEntity.ok(ApiResponse.success("States retrieved successfully", states));
        } catch (Exception e) {
            log.error("Error retrieving states", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", e.getMessage()));
        }
    }

    @GetMapping("/districts")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<List<String>>> getAllDistricts() {
        try {
            List<String> districts = pinCodeLookupService.getAllDistricts();
            return ResponseEntity.ok(ApiResponse.success("Districts retrieved successfully", districts));
        } catch (Exception e) {
            log.error("Error retrieving districts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", e.getMessage()));
        }
    }

    @GetMapping("/states/{state}/districts")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<List<String>>> getDistrictsByState(@PathVariable String state) {
        try {
            List<String> districts = pinCodeLookupService.getDistrictsByState(state);
            return ResponseEntity.ok(ApiResponse.success("Districts for state retrieved successfully", districts));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid state name", e.getMessage()));
        } catch (Exception e) {
            log.error("Error retrieving districts for state: {}", state, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", e.getMessage()));
        }
    }

    // ===== Helper Methods =====

    @PostMapping("/user/populate-location")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<String>> populateUserLocationData(@CurrentUser User currentUser) {
        try {
            User user = userService.findById(currentUser.getId());
            pinCodeLookupService.populateUserLocationData(user);
            return ResponseEntity.ok(ApiResponse.success("User location data populated successfully", "Success"));
        } catch (Exception e) {
            log.error("Error populating user location data for user: {}", currentUser.getId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", e.getMessage()));
        }
    }

    @GetMapping("/user/full-location")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<String>> getUserFullLocation(@CurrentUser User currentUser) {
        try {
            User user = userService.findById(currentUser.getId());
            String fullLocation = pinCodeLookupService.getUserFullLocation(user);
            return ResponseEntity.ok(ApiResponse.success("User full location retrieved successfully", fullLocation));
        } catch (Exception e) {
            log.error("Error getting user full location for user: {}", currentUser.getId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", e.getMessage()));
        }
    }

    @GetMapping("/user/display-location")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<String>> getUserDisplayLocation(@CurrentUser User currentUser) {
        try {
            User user = userService.findById(currentUser.getId());
            String displayLocation = pinCodeLookupService.getUserDisplayLocation(user);
            return ResponseEntity.ok(ApiResponse.success("User display location retrieved successfully", displayLocation));
        } catch (Exception e) {
            log.error("Error getting user display location for user: {}", currentUser.getId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", e.getMessage()));
        }
    }

    // ===== Helper Methods =====

    @PostMapping("/prefixes/states")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<List<String>>> convertStatesToPrefixes(@RequestBody List<String> stateNames) {
        try {
            List<String> prefixes = pinCodeLookupService.convertStatesToPrefixes(stateNames);
            return ResponseEntity.ok(ApiResponse.success("States converted to prefixes successfully", prefixes));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid state names", e.getMessage()));
        } catch (Exception e) {
            log.error("Error converting states to prefixes", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", e.getMessage()));
        }
    }

    @PostMapping("/prefixes/districts")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_DEPARTMENT','ROLE_USER')")
    public ResponseEntity<ApiResponse<List<String>>> convertDistrictsToPrefixes(@RequestBody List<String> districtNames) {
        try {
            List<String> prefixes = pinCodeLookupService.convertDistrictsToPrefixes(districtNames);
            return ResponseEntity.ok(ApiResponse.success("Districts converted to prefixes successfully", prefixes));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid district names", e.getMessage()));
        } catch (Exception e) {
            log.error("Error converting districts to prefixes", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error", e.getMessage()));
        }
    }
}
