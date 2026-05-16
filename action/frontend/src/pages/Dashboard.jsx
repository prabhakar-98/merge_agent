import StatsBar from '../components/StatsBar';
import WorkflowCard from '../components/WorkflowCard';
import HistoryTable from '../components/HistoryTable';
import LoginCard from '../components/LoginCard';
import { useWorkflows } from '../hooks/useWorkflows';
import { useStats } from '../hooks/useStats';

export default function Dashboard({ user }) {
  const { active, history, loading } = useWorkflows();
  const stats = useStats();

  if (!user) {
    return <LoginCard />;
  }

  if (loading) {
    return (
      <div className="loading-spinner">
        <div className="spinner"></div>
      </div>
    );
  }

  return (
    <div>
      <StatsBar stats={stats} />

      {/* Active Workflows */}
      <div className="section-header">
        <h2 className="section-title">
          🔄 Active Workflows
          {active.length > 0 && <span className="count">{active.length}</span>}
        </h2>
      </div>

      {active.length > 0 ? (
        <div className="workflow-grid">
          {active.map((wf) => (
            <WorkflowCard key={wf.id} workflow={wf} />
          ))}
        </div>
      ) : (
        <div className="empty-state" style={{ marginBottom: '40px' }}>
          <div className="empty-state-icon">✨</div>
          <p>No active workflows — the agent is idle</p>
        </div>
      )}

      {/* History */}
      <div className="section-header">
        <h2 className="section-title">
          📋 Resolution History
          {history.length > 0 && <span className="count">{history.length}</span>}
        </h2>
      </div>

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <HistoryTable workflows={history} />
      </div>
    </div>
  );
}
