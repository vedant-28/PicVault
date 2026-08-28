export default function UploadProgressList({ queue, onDismiss }) {
  if (queue.length === 0) return null;

  return (
    <ul className="upload-progress-list">
      {queue.map((entry) => (
        <li key={entry.id} className="upload-progress-row">
          <span className="upload-progress-name">{entry.name}</span>

          {entry.status === 'uploading' && (
            <>
              <div className="upload-progress-track">
                <div className="upload-progress-fill" style={{ width: `${entry.progress}%` }} />
              </div>
              <span className="upload-progress-percent num">{entry.progress}%</span>
            </>
          )}

          {entry.status === 'done' && <span className="upload-progress-done">Done</span>}

          {entry.status === 'error' && (
            <>
              <span className="upload-progress-error">{entry.errorMessage}</span>
              <button
                type="button"
                className="upload-progress-dismiss"
                aria-label={`Dismiss ${entry.name}`}
                onClick={() => onDismiss(entry.id)}
              >
                Dismiss
              </button>
            </>
          )}
        </li>
      ))}
    </ul>
  );
}
