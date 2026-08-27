# ADR-008: Frontend — React 18+ with TypeScript

**Status:** PROPOSED
**Date:** 2026-08-26
**Deciders:** Architect, Lead

---

## Context

The SmartPort platform needs a web-based frontend that provides:

1. **Real-time vessel tracking** — WebSocket-based position updates on an interactive map.
2. **Dashboard views** — berth utilization, cargo status, compliance metrics.
3. **CRUD operations** — vessel management, berth reservations, cargo manifests.
4. **Authentication** — login/register with JWT token management.
5. **Responsive design** — works on desktop (primary) and tablet.

The frontend must communicate with the Spring Cloud Gateway (ADR-006) for all API calls, handle JWT token storage and refresh (ADR-009), and provide a modern, responsive UI for port operators.

Key requirements:
- Component-based architecture for reusability.
- TypeScript for type safety (critical for a team project).
- WebSocket support for real-time updates.
- Rich ecosystem for maps, charts, and data tables.
- Active community and extensive documentation.

## Decision

We adopt **React 18+ with TypeScript** as the frontend framework, built with **Vite** as the build tool.

### Technology Stack

| Component | Library | Purpose |
|-----------|---------|---------|
| Framework | React 18+ | Component-based UI |
| Language | TypeScript 5.x | Type safety |
| Build Tool | Vite 6.x | Fast HMR and builds |
| State Management | Zustand | Lightweight global state |
| Data Fetching | TanStack Query (React Query) | Server state management |
| Routing | React Router 7 | Client-side routing |
| Forms | React Hook Form + Zod | Form validation |
| UI Components | shadcn/ui (Radix + Tailwind) | Accessible component library |
| Charts | Recharts | Dashboard visualizations |
| Maps | Leaflet / React-Leaflet | Vessel tracking map |
| Tables | TanStack Table | Data grids for vessel/cargo lists |
| WebSocket | Socket.io-client | Real-time updates |
| HTTP Client | Axios | API communication |
| Styling | Tailwind CSS 4 | Utility-first CSS |
| Linting | ESLint + Prettier | Code quality |

### Project Structure

```
smartport-ui/
├── public/
├── src/
│   ├── api/              # API client configuration
│   │   ├── axios.ts      # Axios instance with interceptors
│   │   ├── auth.ts       # Auth API calls
│   │   ├── vessels.ts    # Vessel API calls
│   │   ├── berths.ts     # Berth API calls
│   │   └── cargo.ts      # Cargo API calls
│   ├── components/       # Reusable UI components
│   │   ├── ui/           # shadcn/ui components
│   │   ├── layout/       # Header, Sidebar, Footer
│   │   ├── vessels/      # Vessel-specific components
│   │   ├── berths/       # Berth-specific components
│   │   └── charts/       # Dashboard chart components
│   ├── hooks/            # Custom React hooks
│   │   ├── useAuth.ts    # Authentication hook
│   │   ├── useWebSocket.ts  # WebSocket connection hook
│   │   └── useVessels.ts # Vessel data hook
│   ├── pages/            # Route pages
│   │   ├── Login.tsx
│   │   ├── Dashboard.tsx
│   │   ├── Vessels.tsx
│   │   ├── Berths.tsx
│   │   ├── Cargo.tsx
│   │   └── Compliance.tsx
│   ├── stores/           # Zustand stores
│   │   ├── authStore.ts  # Auth state
│   │   └── vesselStore.ts  # Vessel tracking state
│   ├── types/            # TypeScript type definitions
│   │   ├── vessel.ts
│   │   ├── berth.ts
│   │   └── cargo.ts
│   ├── utils/            # Utility functions
│   ├── App.tsx           # Root component
│   └── main.tsx          # Entry point
├── index.html
├── package.json
├── tsconfig.json
├── vite.config.ts
└── tailwind.config.ts
```

### API Communication Pattern

```typescript
// Axios instance with JWT interceptor
const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080/api',
  timeout: 10000,
});

// Request interceptor — attach JWT token
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor — handle 401 and refresh token
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      const refreshToken = localStorage.getItem('refreshToken');
      if (refreshToken) {
        const { data } = await axios.post('/api/auth/refresh', { refreshToken });
        localStorage.setItem('accessToken', data.accessToken);
        error.config.headers.Authorization = `Bearer ${data.accessToken}`;
        return api(error.config);
      }
      // Redirect to login
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
```

### WebSocket for Real-Time Vessel Tracking

```typescript
// useWebSocket.ts
import { useEffect, useState } from 'react';
import { io, Socket } from 'socket.io-client';

export function useVesselTracking() {
  const [positions, setPositions] = useState<Map<string, VesselPosition>>(new Map());

  useEffect(() => {
    const socket: Socket = io('http://localhost:8080/ws/vessels', {
      auth: { token: localStorage.getItem('accessToken') },
    });

    socket.on('position_update', (update: VesselPositionUpdate) => {
      setPositions(prev => new Map(prev).set(update.imoNumber, update.position));
    });

    socket.on('vessel_arrival', (arrival: VesselArrival) => {
      // Show notification toast
    });

    return () => { socket.disconnect(); };
  }, []);

  return { positions };
}
```

## Alternatives Considered

### 1. Vue.js 3
A progressive JavaScript framework.
- **Pros:** Gentle learning curve, excellent documentation, Composition API is powerful, smaller bundle size.
- **Cons:** Smaller ecosystem than React, fewer UI component libraries, TypeScript support is good but not as mature as React's, fewer job-market-aligned skills.
- **Verdict:** Rejected — React's ecosystem and job market alignment are stronger.

### 2. Angular 17+
A full-featured framework by Google.
- **Pros:** Complete framework (routing, forms, HTTP all built-in), strong TypeScript support, RxJS for reactive patterns.
- **Cons:** Steep learning curve, verbose syntax, heavy bundle size, overkill for our UI complexity, team limited Angular experience.
- **Verdict:** Rejected — too heavyweight for the project scope.

### 3. Thymeleaf (Server-Rendered)
Server-side rendering with Spring Boot.
- **Pros:** No separate frontend build, single deployment unit, Spring Boot native support.
- **Cons:** No SPA experience, limited interactivity, no WebSocket real-time updates (without HTMX), poor developer experience for complex UIs, doesn't demonstrate SOA frontend separation.
- **Verdict:** Rejected — doesn't provide the interactive, real-time experience needed.

### 4. Svelte / SvelteKit
A compile-time framework with minimal runtime.
- **Pros:** Smallest bundle size, fastest runtime performance, simple syntax, no virtual DOM.
- **Cons:** Smaller ecosystem, fewer UI component libraries, limited job market, team has no Svelte experience, SvelteKit is newer and less stable.
- **Verdict:** Rejected — ecosystem maturity favors React.

### 5. Next.js (React with SSR)
React with server-side rendering and API routes.
- **Pros:** SEO-friendly, API routes eliminate separate backend, image optimization, edge rendering.
- **Cons:** Overkill for a dashboard application, unnecessary SSR complexity, we already have a Spring Boot backend, adds Node.js to the stack.
- **Verdict:** Rejected — we don't need SSR; our backend is Spring Boot.

## Rationale

React 18+ with TypeScript is the optimal choice based on:

1. **Ecosystem size:** React has the largest frontend ecosystem with 200K+ npm packages.
2. **TypeScript integration:** React's TypeScript support is mature and well-documented, with excellent IDE support.
3. **Component reusability:** React's component model enables building a reusable UI component library (via shadcn/ui).
4. **Real-time support:** Socket.io-client integrates seamlessly with React hooks for WebSocket updates.
5. **Map integration:** React-Leaflet provides excellent vessel tracking map capabilities.
6. **Team proficiency:** 3 of 4 team members have React experience from coursework or personal projects.
7. **Job market alignment:** React is the most in-demand frontend skill, maximizing the project's resume value.
8. **shadcn/ui:** Provides accessible, customizable components built on Radix primitives with Tailwind CSS styling.
9. **Vite:** Fast build times (<1s HMR) improve developer experience significantly.

## Consequences

### Positive
- **Rich ecosystem** — every UI need has a well-maintained React solution.
- **TypeScript safety** — compile-time error catching prevents runtime bugs.
- **Component reusability** — shadcn/ui components can be customized and shared across pages.
- **Real-time updates** — Socket.io-client provides reliable WebSocket communication.
- **Developer experience** — Vite's fast HMR and React DevTools streamline development.
- **Responsive design** — Tailwind CSS enables rapid responsive layout creation.
- **Testing support** — React Testing Library and Vitest provide comprehensive testing tools.

### Negative
- **Build complexity** — requires Node.js, npm/pnpm, and a build step (vs. server-rendered templates).
- **Learning curve** — React's mental model (hooks, state, effects) can be challenging for beginners.
- **Bundle size** — React + dependencies add ~50KB gzipped to the initial load (mitigated by code splitting).
- **State management complexity** — multiple state layers (local, global, server state) can be confusing (mitigated by clear separation: Zustand for global, TanStack Query for server state).
- **SEO limitations** — SPA doesn't provide server-rendered HTML (not critical for an internal port management system).

## Related

- ADR-001: Architecture Style — SBA (frontend consumes service APIs via gateway)
- ADR-006: API Gateway — Spring Cloud Gateway (frontend calls gateway)
- ADR-009: Authentication — JWT (frontend manages tokens)
- React Documentation: https://react.dev/
- TypeScript Documentation: https://www.typescriptlang.org/docs/
- shadcn/ui: https://ui.shadcn.com/
