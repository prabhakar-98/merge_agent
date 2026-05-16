function formatTime(dateStr) {
  if (!dateStr) return '';
  return new Date(dateStr).toLocaleTimeString('en-US', {
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  });
}

export default function WorkflowTimeline({ events }) {
  if (!events || events.length === 0) {
    return (
      <div className="empty-state">
        <div className="empty-state-icon">🕐</div>
        <p>No events recorded yet</p>
      </div>
    );
  }

  return (
    <div className="timeline">
      {events.map((evt, i) => {
        const type = (evt.eventType || 'text').toLowerCase();
        return (
          <div
            className="timeline-event"
            key={evt.id}
            style={{ '--i': i }}
          >
            <div className={`timeline-dot ${type}`}></div>
            <div className="timeline-content">
              <div className="timeline-header">
                <span className="timeline-author">{evt.author || 'system'}</span>
                <span className="timeline-time">{formatTime(evt.createdAt)}</span>
              </div>
              <div className="timeline-summary">
                <span className={`timeline-type ${type}`}>{type.replace('_', ' ')}</span>
                {evt.contentSummary || '—'}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
