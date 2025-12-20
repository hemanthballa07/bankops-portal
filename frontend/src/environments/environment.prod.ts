export const environment = {
  production: true,
  apiUrl: (window as any).__ENV__?.API_URL || 'https://bankops-portal-backend.azurewebsites.net/api'
};

