import { useState, useEffect, useRef } from 'react';
import { api } from '../api/client';

export function useWorkflows(pollInterval = 5000) {
  const [active, setActive] = useState([]);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const intervalRef = useRef(null);

  const fetchAll = async () => {
    try {
      const [activeData, historyData] = await Promise.all([
        api.getActiveWorkflows(),
        api.getWorkflowHistory(),
      ]);
      setActive(activeData || []);
      setHistory(historyData || []);
    } catch (err) {
      console.error('Failed to fetch workflows:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAll();
    intervalRef.current = setInterval(fetchAll, pollInterval);
    return () => clearInterval(intervalRef.current);
  }, [pollInterval]);

  return { active, history, loading, refresh: fetchAll };
}
