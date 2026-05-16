export default function StatusBadge({ status }) {
  const labels = {
    COMPLETED: 'Completed',
    FAILED: 'Failed',
    ESCALATED: 'Escalated',
    RUNNING: 'Running',
    QUEUED: 'Queued',
    HUMAN_IN_LOOP: 'Human Review',
  };

  const key = (status || 'QUEUED').toLowerCase();

  return (
    <span className={`badge badge-${key}`}>
      <span className="badge-dot"></span>
      {labels[status] || status}
    </span>
  );
}
