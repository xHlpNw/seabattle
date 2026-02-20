/**
 * Development environment.
 * API and WebSocket point to backend on port 8080 (or same host when testing from network).
 */
export const environment = {
  production: false,
  /**
   * Base URL for REST API (no trailing slash).
   * Empty string = same origin (relative URLs like /api/...).
   */
  apiBaseUrl: getDevApiBaseUrl(),
};

function getDevApiBaseUrl(): string {
  if (typeof window === 'undefined') return 'http://localhost:8080';
  const host = window.location.hostname;
  return host === 'localhost' ? 'http://localhost:8080' : `http://${host}:8080`;
}
