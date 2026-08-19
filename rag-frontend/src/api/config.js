// Where the backend API lives. This one constant is what makes the
// frontend and backend genuinely separate, independently-run apps instead
// of one bundled together.
//
// In development (npm run dev), this points straight at Spring Boot on
// :8080 -- the two apps run as two separate processes and talk to each
// other over a real cross-origin request (see SecurityConfig.java on the
// backend, which explicitly allows that origin via CORS).
//
// In a production build (npm run build), this is empty on purpose: at that
// point Spring Boot serves the built frontend itself (see
// rag-assistant/src/main/resources/static), so the API ends up on the exact
// same origin as the page, and a relative path is all that's needed.
//
// Vite automatically loads .env.development for `npm run dev` and
// .env.production for `npm run build` -- nothing to switch by hand, the
// right value is picked up based on which command you ran.
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "";
