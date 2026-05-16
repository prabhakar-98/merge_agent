const BASE = '';

async function request(path, options = {}) {
  const res = await fetch(`${BASE}${path}`, {
    credentials: 'include',
    headers: { 'Accept': 'application/json', ...options.headers },
    ...options,
  });
  if (!res.ok) {
    throw new Error(`API error ${res.status}: ${res.statusText}`);
  }
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

export const api = {
  // Auth
  getAuthStatus: () => request('/oauth/status'),
  getLoginUrl: () => `${BASE}/oauth/login`,
  exchangeAuthCode: (code) => request('/oauth/exchange', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code }),
  }),
  logout: () => request('/oauth/logout', { method: 'POST' }),

  // Dashboard
  getActiveWorkflows: () => request('/api/dashboard/workflows/active'),
  getWorkflowHistory: () => request('/api/dashboard/workflows/history'),
  getWorkflow: (id) => request(`/api/dashboard/workflows/${id}`),
  getWorkflowEvents: (id) => request(`/api/dashboard/workflows/${id}/events`),
  getStats: () => request('/api/dashboard/stats'),
};
