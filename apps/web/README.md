# Govlyx - Web Platform

This is the frontend application for Govlyx, built with **React**, **TypeScript**, and **Vite**. It provides a highly responsive, fast, and feature-rich user interface for community interactions, posts, and real-time messaging.

## âœ¨ Key Features

- **Fast & Optimized:** Bundled and served using Vite for lightning-fast HMR and optimized production builds.
- **Progressive Web App (PWA):** Configured with `vite-plugin-pwa` for offline capabilities, caching, and installability.
- **Type-Safe:** Written in strict TypeScript.
- **Modern Styling:** Styled using TailwindCSS for rapid, utility-first design.
- **Real-Time Capabilities:** Integrates with the backend via WebSockets (SockJS + STOMP) for live notifications and chat.

## 🛠️ Development

This project is managed as an Nx application within the root monorepo.

### Starting the Dev Server
From the root of the monorepo, run:
```bash
npm run dev:web
```
Or, using Nx directly:
```bash
npx nx dev web
```

### Building for Production
The production build compiles the TypeScript code and bundles the application into the `dist/apps/web` directory at the root of the workspace.
```bash
npx nx build web
```

### Environment Variables
Environment variables should be placed in a `.env` file at the root of `apps/web/`. All variables exposed to the client must be prefixed with `VITE_`.
```env
VITE_API_URL=http://localhost:8080
```

## 📁 Structure Highlights
- `src/components/`: Reusable UI components (Posts, Comments, Modals, etc.)
- `src/pages/`: Route-level screen components (Feed, Profile, Communities, etc.)
- `src/utils/`: Helpers, constants, and API configuration.
