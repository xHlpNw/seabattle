/**
 * Production environment.
 * API and WebSocket use same origin (reverse proxy must forward /api to backend).
 */
export const environment = {
  production: true,
  /** Same origin — use relative URLs: /api/... */
  apiBaseUrl: '',
};
