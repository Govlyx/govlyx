# Govlyx

Welcome to **Govlyx**, a modern community-driven social platform designed to connect people through shared interests, neighborhoods, and secure discussions.

This repository is an [Nx](https://nx.dev) Monorepo that houses both the React frontend and the Spring Boot backend, providing unified caching, continuous integration, and seamless developer experience.

## 🏗️ Architecture at a Glance

```text
govlyx/
â”œâ”€â”€ apps/
â”‚   â”œâ”€â”€ web/              # React + Vite + TypeScript Frontend
â”‚   â””â”€â”€ api/              # Spring Boot + Java 21 Backend
â”œâ”€â”€ libs/
â”‚   â””â”€â”€ api-contracts/    # Shared OpenAPI specs and generated DTOs
â”œâ”€â”€ package.json          # Root scripts & dependencies
â””â”€â”€ nx.json               # Nx configuration & cache rules
```

## 🚀 Quick Start

Ensure you have [Node.js](https://nodejs.org/) (v18+) and [Java 21](https://jdk.java.net/21/) installed. 

Install the workspace dependencies:
```bash
npm install
```

### Running the Frontend
Starts the Vite development server on `http://localhost:5173`:
```bash
npm run dev:web
```

### Running the Backend
Starts the Spring Boot application (ensure your PostgreSQL database is running):
```bash
npm run dev:api
```

### Building for Production
Build both the frontend and backend simultaneously using Nx's parallel execution:
```bash
npm run build
```

## 📚 Documentation
- [Frontend Documentation](./apps/web/README.md)
- [Backend Documentation](./apps/api/README.md)

## 🛠️ Tech Stack
- **Monorepo Tooling:** [Nx](https://nx.dev)
- **Frontend:** React, TypeScript, Vite, TailwindCSS, PWA
- **Backend:** Java 21, Spring Boot, PostgreSQL, JWT Authentication, WebSockets (STOMP), Brevo API
