import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { api } from '../api/client';
import StatusBadge from '../components/StatusBadge';
import WorkflowTimeline from '../components/WorkflowTimeline';

function formatDate(dateStr) {
  if (!dateStr) return '—';
  return new Date(dateStr).toLocaleString();
}

export default function WorkflowDetail() {
  const { id } = useParams();
  const [workflow, setWorkflow] = useState(null);
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [wf, evts] = await Promise.all([
          api.getWorkflow(id),
          api.getWorkflowEvents(id),
        ]);
        setWorkflow(wf);
        setEvents(evts || []);
      } catch (err) {
        console.error('Failed to fetch workflow detail:', err);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
    // Poll if running
    const interval = setInterval(fetchData, 3000);
    return () => clearInterval(interval);
  }, [id]);

  if (loading) {
    return (
      <div className="loading-spinner">
        <div className="spinner"></div>
      </div>
    );
  }

  if (!workflow) {
    return (
      <div className="empty-state">
        <div className="empty-state-icon">❌</div>
        <p>Workflow not found</p>
        <Link to="/" className="btn btn-secondary" style={{ marginTop: '16px' }}>
          ← Back to Dashboard
        </Link>
      </div>
    );
  }

  const isActive = ['RUNNING', 'QUEUED'].includes(workflow.status);

  return (
    <div>
      <Link to="/" className="back-link">← Back to Dashboard</Link>

      <div className="detail-header">
        <div className="detail-title">
          {workflow.owner}/{workflow.repo} #{workflow.prNumber}
          <StatusBadge status={workflow.status} />
        </div>

        {workflow.prTitle && (
          <p style={{ color: 'var(--text-secondary)', marginBottom: '16px' }}>
            {workflow.prTitle}
          </p>
        )}

        <div className="detail-meta">
          <span>🌿 {workflow.featureBranch} → {workflow.baseBranch}</span>
          <span>🕐 Started: {formatDate(workflow.startedAt)}</span>
          {workflow.completedAt && (
            <span>✅ Completed: {formatDate(workflow.completedAt)}</span>
          )}
          {workflow.currentStep && (
            <span>📍 Step: {workflow.currentStep}</span>
          )}
        </div>
      </div>

      {/* Error info */}
      {workflow.errorMessage && (
        <div className="card" style={{
          borderColor: 'rgba(248, 81, 73, 0.3)',
          marginBottom: '24px',
          background: 'rgba(248, 81, 73, 0.05)'
        }}>
          <div className="card-title" style={{ color: 'var(--status-failed)' }}>
            ❌ Error: {workflow.errorCategory || 'Unknown'}
          </div>
          <p style={{ color: 'var(--text-secondary)', fontSize: '13px', marginTop: '8px' }}>
            {workflow.errorMessage}
          </p>
        </div>
      )}

      {/* Agent Response */}
      {workflow.agentResponse && (
        <div className="detail-response" style={{ marginBottom: '32px' }}>
          <h3 className="section-title" style={{ marginBottom: '12px' }}>💬 Agent Response</h3>
          <pre>{workflow.agentResponse}</pre>
        </div>
      )}

      {/* Event Timeline */}
      <div className="section-header">
        <h3 className="section-title">
          🕐 Event Timeline
          {events.length > 0 && <span className="count">{events.length}</span>}
        </h3>
      </div>

      <WorkflowTimeline events={events} />
    </div>
  );
}
