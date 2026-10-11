// Server environment - API URL will be set dynamically at runtime
// This allows the app to work when accessed via server IP (e.g., http://40.192.26.206:4200)
// IMPORTANT: Always use HTTP (not HTTPS) for API calls
export const environment = {
  production: false,
  // This will be set dynamically in app initialization
  apiUrl: 'http://localhost:1104/api' // Default, will be overridden
};