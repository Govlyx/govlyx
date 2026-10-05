package com.govlyx.AI.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pincode_lookup", indexes = {
        @Index(name = "idx_state", columnList = "state"),
        @Index(name = "idx_district", columnList = "district"),
        @Index(name = "idx_city", columnList = "city"),
        @Index(name = "idx_coordinates", columnList = "latitude, longitude"),
        @Index(name = "idx_active", columnList = "is_active"),
        @Index(name = "idx_state_district", columnList = "state, district"),
        @Index(name = "idx_area_search", columnList = "area_name, city")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PincodeLookup {

    @Id
    @Column(name = "pincode", length = 6)
    private String pincode;

    @Column(name = "area_name", nullable = false, length = 100)
    private String areaName;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "district", nullable = false, length = 100)
    private String district;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "urban_cluster_id", length = 100)
    private String urbanClusterId;

    @Column(name = "urban_cluster_name", length = 100)
    private String urbanClusterName;

    @Column(name = "settlement_type", nullable = false, length = 20, columnDefinition = "varchar(20) default 'URBAN'")
    @Builder.Default
    private String settlementType = "URBAN";

    @Column(name = "is_cantonment", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean isCantonment = false;

    @Column(name = "is_hill_state", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean isHillState = false;

    @Column(name = "is_active", nullable = false, columnDefinition = "boolean")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ===== Helper Methods for Backward Compatibility =====

    /**
     * Get location in "District State" format for backward compatibility
     * This matches your existing Post and User location format
     */
    public String getLocationName() {
        return district + " " + state;
    }

    /**
     * Get full hierarchical location string
     */
    public String getFullLocationName() {
        StringBuilder location = new StringBuilder();
        location.append(areaName);

        if (city != null && !city.trim().isEmpty()) {
            location.append(", ").append(city);
        }

        location.append(", ").append(district).append(", ").append(state);
        return location.toString();
    }

    /**
     * Get display location for UI
     */
    public String getDisplayLocation() {
        return String.format("%s (%s), %s, %s",
                areaName,
                pincode,
                district,
                state);
    }

    /**
     * Get location for government department use
     */
    public String getDepartmentLocation() {
        return String.format("%s, %s, %s", areaName, district, state);
    }

    // ===== Geographic Helper Methods =====

    /**
     * Check if coordinates are available
     */
    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    /**
     * Get coordinates as array [latitude, longitude]
     */
    public BigDecimal[] getCoordinatesArray() {
        if (hasCoordinates()) {
            return new BigDecimal[]{latitude, longitude};
        }
        return null;
    }

    /**
     * Calculate approximate distance to another pincode (in km)
     * Using Haversine formula approximation
     */
    public double calculateDistanceTo(PincodeLookup other) {
        if (!this.hasCoordinates() || !other.hasCoordinates()) {
            return -1; // Cannot calculate
        }

        double lat1Rad = Math.toRadians(this.latitude.doubleValue());
        double lat2Rad = Math.toRadians(other.latitude.doubleValue());
        double deltaLatRad = Math.toRadians(other.latitude.subtract(this.latitude).doubleValue());
        double deltaLonRad = Math.toRadians(other.longitude.subtract(this.longitude).doubleValue());

        double a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return 6371 * c; // Earth radius in km
    }

    // ===== Validation Methods =====

    /**
     * Check if this is a valid Indian pincode
     */
    public boolean isValidPincode() {
        return pincode != null && pincode.matches("\\d{6}");
    }

    /**
     * Check if this pincode is in a specific state
     */
    public boolean isInState(String checkState) {
        return state != null && state.equalsIgnoreCase(checkState);
    }

    /**
     * Check if this pincode is in a specific district
     */
    public boolean isInDistrict(String checkDistrict) {
        return district != null && district.equalsIgnoreCase(checkDistrict);
    }

    /**
     * Check if this pincode is in a specific city
     */
    public boolean isInCity(String checkCity) {
        return city != null && city.equalsIgnoreCase(checkCity);
    }

    // ===== Search Helper Methods =====

    /**
     * Check if area name contains search term
     */
    public boolean areaContains(String searchTerm) {
        return areaName != null &&
                areaName.toLowerCase().contains(searchTerm.toLowerCase());
    }

    /**
     * Get searchable text for full-text search
     */
    public String getSearchableText() {
        StringBuilder searchText = new StringBuilder();
        searchText.append(pincode).append(" ");
        searchText.append(areaName).append(" ");
        if (city != null) searchText.append(city).append(" ");
        searchText.append(district).append(" ");
        searchText.append(state).append(" ");
        return searchText.toString().toLowerCase();
    }

    // ===== Administrative Helper Methods =====

    /**
     * Deactivate this pincode
     */
    public void deactivate() {
        this.isActive = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Reactivate this pincode
     */
    public void reactivate() {
        this.isActive = true;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Update coordinates
     */
    public void updateCoordinates(BigDecimal lat, BigDecimal lon) {
        this.latitude = lat;
        this.longitude = lon;
        this.updatedAt = LocalDateTime.now();
    }

    // ===== Lifecycle Callbacks =====
    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ===== toString for debugging =====
    @Override
    public String toString() {
        return String.format("PincodeLookup{pincode='%s', area='%s', city='%s', district='%s', state='%s'}",
                pincode, areaName, city, district, state);
    }


    // =========================================================================
    // EQUALS & HASHCODE
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PincodeLookup)) return false;
        PincodeLookup other = (PincodeLookup) o;
        return pincode != null && pincode.equals(other.getPincode());
    }

    @Override
    public int hashCode() {
        return pincode != null ? pincode.hashCode() : 31;
    }
}
