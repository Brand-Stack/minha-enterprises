/**
 * Local Deployment Environment Configuration
 * 
 * Used when the application is deployed on client's local machine.
 * In this mode:
 * - Frontend is served as static files by Spring Boot
 * - API is on the same origin (no CORS issues)
 * - Everything runs through single port (1104)
 */
export const environment = {
  production: true,
  // API URL - same origin, /api prefix is handled by ApiPrefixFilter
  apiUrl: '/api'
};
