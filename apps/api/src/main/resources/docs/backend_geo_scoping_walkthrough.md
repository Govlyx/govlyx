# Backend Geo-Scoping Implementation Walkthrough

I have successfully applied all the backend architectural changes requested in the plan, optimizing the database layer for geographic queries and setting up the entity models to handle our new 4-layer feed strategy. No frontend changes have been made, as requested.

## What was completed

### 1. SQL Migration and Pincode Clustering

I created the SQL migration script which performs the clustering logic.
You can find it here: [pincode_clustering.sql](file:///c:/Users/Madhav/Desktop/Springboot%20project/AI/AI/src/main/resources/pincode_clustering.sql)

> [!NOTE]
> The clustering script generates unique `urban_cluster_id` strings by combining city and district strings. For rural areas, it falls back to a district-level cluster. This ensures the `CITY` scope always has a valid bounding bucket to query against.

### 2. Entity Updates

We updated all the foundational JPA entities to support the new features:

- **[User.java](file:///c:/Users/Madhav/Desktop/Springboot%20project/AI/AI/src/main/java/com/JanSahayak/AI/model/User.java)**: Added `homeLatitude` and `homeLongitude` as `BigDecimal` types to handle high-precision GPS coordinates.
- **[SocialPost.java](file:///c:/Users/Madhav/Desktop/Springboot%20project/AI/AI/src/main/java/com/JanSahayak/AI/model/SocialPost.java)**: Added `latitude`, `longitude`, `qaPosterScope`, `targetUrbanClusterId`, and `targetDistrict`. Updated the `inheritLocationFromUser` method so posts automatically adopt the author's GPS coordinates upon creation.
- **[PincodeLookup.java](file:///c:/Users/Madhav/Desktop/Springboot%20project/AI/AI/src/main/java/com/JanSahayak/AI/model/PincodeLookup.java)**: Added properties for `urbanClusterId`, `urbanClusterName`, `settlementType`, `isCantonment`, and `isHillState` to power granular location filtering.

### 3. High-Performance Repository Queries

I refactored the Q&A feed queries in **[SocialPostRepo.java](file:///c:/Users/Madhav/Desktop/Springboot%20project/AI/AI/src/main/java/com/JanSahayak/AI/repository/SocialPostRepo.java)** to replace the old prefix-based matching with proper relational and geographic queries:

> [!TIP]
> **Bounding Box Math:** Notice in `findNearbyPostsUsingGPS` we pre-filter using `sp.latitude BETWEEN :minLat AND :maxLat` before calculating the Haversine distance (`acos(cos...)`). This prevents the database from calculating trigonometric functions across 5 million rows—it will only calculate the math for posts inside a rough physical square.

- `findNearbyPostsUsingGPS`: Native SQL query implementing Haversine formula + Bounding Box pre-filtering.
- `findQAPostsByCity`: Uses a subquery to find all pincodes belonging to the same city name.
- `findQAPostsByUrbanCluster`: Uses a subquery to find all pincodes within the same `urbanClusterId`.
- `findQAPostsByRealDistrict`: Uses a subquery to map posts inside a target district.

### 4. Service Logic (Cold Starts & Data Drift)

I updated **[SocialPostService.java](file:///c:/Users/Madhav/Desktop/Springboot%20project/AI/AI/src/main/java/com/JanSahayak/AI/service/SocialPostService.java)**:

- Replaced the old Pincode and District logic in `getNeighborhoodQAFeed` with the new scope cascading logic (NEARBY, AREA, CITY, DISTRICT).
- **Cold Start Algorithm implemented**: If a user queries the `NEARBY` scope and returns 0 posts at a 5km radius, the system dynamically expands the bounding box to 15km, and finally to 30km, guaranteeing the feed doesn't appear "dead" in rural regions.
- Added `syncGPSFromPincode(User user)` helper method. This handles the scenario where a user updates their Pincode but declines GPS access—it queries the `PincodeLookup` table and grabs the coordinates for the center of that pincode, forcibly synchronizing their `homeLatitude` and `homeLongitude` so their feed keeps working.

## Next Steps

All backend logic is fully wired. When you are ready to tackle the frontend, we can connect the React UI elements (Pincode warning message and scope selection pills) to these new endpoints.
