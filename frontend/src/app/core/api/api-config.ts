import { environment } from '../../../environments/environment';

/**
 * Base URL for REST API (no trailing slash).
 * In production: '' (relative). In development: e.g. 'http://localhost:8080'.
 */
export function getApiBaseUrl(): string {
  return environment.apiBaseUrl;
}

/**
 * Base URL for WebSocket (no trailing slash, no path).
 * In production: same origin (wss/https or ws/http). In development: e.g. 'ws://localhost:8080'.
 */
export function getWsBaseUrl(): string {
  if (environment.apiBaseUrl) {
    return environment.apiBaseUrl.replace(/^http/, 'ws');
  }
  if (typeof window !== 'undefined') {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}`;
  }
  return 'ws://localhost:8080';
}
