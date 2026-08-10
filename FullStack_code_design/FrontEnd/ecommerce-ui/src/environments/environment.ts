// Development environment
export const environment = {
  production: false,
  // Proxy config forwards /api → localhost:8080 (Spring Boot)
  // See proxy.conf.json — no CORS issues in local dev
  apiUrl: '/api/v1',
};
