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

export default function HistoryTable({ workflows }) {
  const navigate = useNavigate();

  if (!workflows || workflows.length === 0) {
    return (
      <div className="empty-state">
        <div className="empty-state-icon">📋</div>
        <p>No merge resolution history yet</p>
      </div>
    );
  }

  return (
    <table className="history-table">
      <thead>
        <tr>
          <th>Status</th>
          <th>Repository</th>
          <th>PR</th>
          <th>Title</th>
          <th>Branches</th>
          <th>Time</th>
        </tr>
      </thead>
      <tbody>
        {workflows.map((wf) => (
          <tr key={wf.id} onClick={() => navigate(`/workflow/${wf.id}`)}>
            <td><StatusBadge status={wf.status} /></td>
            <td className="repo-cell">{wf.owner}/{wf.repo}</td>
            <td className="pr-cell">#{wf.prNumber}</td>
            <td>{wf.prTitle ? wf.prTitle.substring(0, 60) : '—'}</td>
            <td style={{ fontFamily: 'monospace', fontSize: '12px', color: 'var(--text-muted)' }}>
              {wf.featureBranch} → {wf.baseBranch}
            </td>
            <td>{timeAgo(wf.startedAt)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
