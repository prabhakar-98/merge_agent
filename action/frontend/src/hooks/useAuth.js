import { useState, useEffect, useCallback, useRef } from 'react';
import { api } from '../api/client';

const USER_STORAGE_KEY = 'github_user';

export function useAuth() {
  const [user, setUser] = useState(() => {
    const stored = localStorage.getItem(USER_STORAGE_KEY);
    return stored ? JSON.parse(stored) : null;
  });
  const [loading, setLoading] = useState(true);
  const exchanged = useRef(false);

  const checkAuth = useCallback(async () => {
    try {
      const data = await api.getAuthStatus();
      if (data && data.authenticated) {
        const u = data.user || data;
        setUser(u);
        localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(u));
      } else {
        const stored = localStorage.getItem(USER_STORAGE_KEY);
        if (stored) {
          setUser(JSON.parse(stored));
        } else {
          setUser(null);
        }
      }
    } catch {
      const stored = localStorage.getItem(USER_STORAGE_KEY);
      if (stored) {
        setUser(JSON.parse(stored));
      } else {
        setUser(null);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const authCode = params.get('auth_code');

    if (authCode && !exchanged.current) {
      exchanged.current = true;
      window.history.replaceState({}, '', '/');
      api.exchangeAuthCode(authCode)
        .then((data) => {
          if (data && data.authenticated) {
            const u = data.user;
            setUser(u);
            localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(u));
          }
        })
        .catch(() => {})
        .finally(() => setLoading(false));
    } else {
      checkAuth();
    }
  }, [checkAuth]);

  const logout = async () => {
    try {
      await api.logout();
    } catch { /* ignore */ }
    setUser(null);
    localStorage.removeItem(USER_STORAGE_KEY);
  };

  return { user, loading, logout, checkAuth };
}
