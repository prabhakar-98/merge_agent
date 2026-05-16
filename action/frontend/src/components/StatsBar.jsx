export default function StatsBar({ stats }) {
  return (
    <div className="stats-bar">
      <div className="stat-card">
        <div className="stat-value">{stats.total}</div>
        <div className="stat-label">Total Runs</div>
      </div>
      <div className="stat-card">
        <div className="stat-value green">{stats.completed}</div>
        <div className="stat-label">Resolved</div>
      </div>
      <div className="stat-card">
        <div className="stat-value blue">{stats.active}</div>
        <div className="stat-label">Active</div>
      </div>
      <div className="stat-card">
        <div className="stat-value red">{stats.failed}</div>
        <div className="stat-label">Failed</div>
      </div>
      <div className="stat-card">
        <div className="stat-value orange">{stats.humanInLoop}</div>
        <div className="stat-label">Human Review</div>
      </div>
    </div>
  );
}
