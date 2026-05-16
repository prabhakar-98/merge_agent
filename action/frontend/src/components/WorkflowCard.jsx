import { useNavigate } from 'react-router-dom';
import StatusBadge from './StatusBadge';

function timeAgo(dateStr) {
  if (!dateStr) return '';
  const diff = Date.now() - new Date(dateStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  const days = Math.floor(hrs / 24);
  return `${days}d ago`;
}

export default function WorkflowCard({ workflow }) {
  const navigate = useNavigate();
  const status = (workflow.status || 'QUEUED').toLowerCase();
  const isActive = ['running', 'queued'].includes(status);

  return (
    <div
      className={`card workflow-card status-${status}`}
      onClick={() => navigate(`/workflow/${workflow.id}`)}
    >
      <div className="workflow-header">
        <div>
          <div className="workflow-repo">
            {workflow.owner}/{workflow.repo}
            <span className="pr-number"> #{workflow.prNumber}</span>
          </div>
        </div>
        <StatusBadge status={workflow.status} />
      </div>

      {workflow.prTitle && (
        <div className="workflow-pr-title">{workflow.prTitle}</div>
      )}

      {workflow.currentStep && (
        <div className="workflow-step">
          <div className={`workflow-step-icon ${isActive ? 'running' : ''}`}>
            {isActive ? '⚡' : '✓'}
          </div>
          {workflow.currentStep}
        </div>
      )}

      <div className="workflow-footer">
        <div className="workflow-branches">
          {workflow.featureBranch} → {workflow.baseBranch}
        </div>
        <div className="workflow-time">
          {timeAgo(workflow.startedAt)}
        </div>
      </div>
    </div>
  );
}
