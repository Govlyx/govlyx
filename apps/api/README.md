# Govlyx - API / Backend

This is the backend service for Govlyx, powered by **Java 21** and **Spring Boot**. It handles authentication, data persistence, real-time events, and business logic for the platform.

## Key Features

- **Robust Security:** JWT-based stateless authentication, Spring Security, and role-based access control (Admin, Moderator, User). Zero-Knowledge blind indexing for secure, encrypted emails.
- **High Performance:** Utilizes Caffeine caching for frequently accessed data to minimize database load.
- **Relational Data:** PostgreSQL database structured with JPA/Hibernate for complex community hierarchies, posts, and tagging.
- **Real-Time WebSockets:** STOMP over WebSockets for live community chat and instant notifications.
- **External Integrations:** 
  - **Brevo API:** Transactional email delivery for verifications.
  - **Cloudinary:** Media upload and handling.
  - **Razorpay:** Secure payment processing.

## Development

This project uses the Maven wrapper (`mvnw`) but is orchestrated via the root Nx monorepo.

### Prerequisites
- Java 21 JDK
- PostgreSQL running locally (check `application.properties` for port/credentials)

### Starting the Server
From the root of the monorepo, run:
```bash
npm run dev:api
```
*(On Windows, this automatically uses `mvnw.cmd spring-boot:run`)*

### Environment Variables
Copy `.env.example` to `.env` in the `apps/api/` directory and supply the required values. Never commit the `.env` file.
```env
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/govlyx
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=replace_me
JWT_SECRET=replace_with_a_long_random_value
```
Set the same variables in your deployment environment for production.

### Building the JAR
To package the application into an executable `.jar` file (skipping tests):
```bash
npx nx build api
```
The resulting artifact will be located in `apps/api/target/`.

## Structure Highlights
- `src/main/java/com/govlyx/AI/controller`: REST API endpoints.
- `src/main/java/com/govlyx/AI/service`: Core business logic, caching strategies, and API wrappers.
- `src/main/java/com/govlyx/AI/security`: JWT filters, entry points, and token management.
- `src/main/java/com/govlyx/AI/model`: JPA Entities mapping the database schema.
