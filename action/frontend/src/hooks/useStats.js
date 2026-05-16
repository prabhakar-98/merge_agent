import { useState, useEffect } from 'react';
import { api } from '../api/client';

export function useStats(pollInterval = 10000) {
  const [stats, setStats] = useState({
    total: 0, completed: 0, failed: 0, escalated: 0,
    humanInLoop: 0, running: 0, queued: 0, active: 0,
  });

  useEffect(() => {
    const fetch = async () => {
      try {
        const data = await api.getStats();
        if (data) setStats(data);
      } catch { /* ignore */ }
    };
    fetch();
    const id = setInterval(fetch, pollInterval);
    return () => clearInterval(id);
  }, [pollInterval]);

  return stats;
}
