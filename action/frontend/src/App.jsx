import { Routes, Route } from 'react-router-dom';
import Header from './components/Header';
import Dashboard from './pages/Dashboard';
import WorkflowDetail from './pages/WorkflowDetail';
import { useAuth } from './hooks/useAuth';

export default function App() {
  const { user, loading, logout } = useAuth();

  if (loading) {
    return (
      <div className="app-container">
        <div className="loading-spinner" style={{ minHeight: '80vh' }}>
          <div className="spinner"></div>
        </div>
      </div>
    );
  }

  return (
    <div className="app-container">
      <Header user={user} onLogout={logout} />
      <Routes>
        <Route path="/" element={<Dashboard user={user} />} />
        <Route path="/workflow/:id" element={<WorkflowDetail />} />
      </Routes>
    </div>
  );
}
