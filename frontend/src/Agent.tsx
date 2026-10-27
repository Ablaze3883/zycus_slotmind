import { ActivityFeed } from './Queue';
import { Logo, Warn } from './icons';
import { useDesk } from './useDesk';

export function AgentPage() {
  const { data, error, lastOk, agoSec, refresh } = useDesk();

  return (
    <div>
      <header className="header">
        <div className="header-inner agent-header">
          <a className="brand" href="/">
            <div className="brand-mark"><Logo /></div>
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <span style={{ fontWeight: 700, fontSize: 16 }}>SlotMind</span>
              <span className="small muted">Host / Agent</span>
            </div>
          </a>
          <a className="btn agent-back" href="/">Front desk</a>
          <div className="agent-status">
            {error ? (
              <div className="pill err" role="status"><span className="d" style={{ background: '#F04438' }} /><span>Connection lost</span></div>
            ) : lastOk ? (
              <div className="pill ok" role="status"><span className="d pulse" style={{ background: '#12B76A' }} /><span>Live · synced {agoSec}s ago</span></div>
            ) : (
              <div className="pill ok"><span className="d pulse" style={{ background: '#98A2B3' }} /><span>Connecting…</span></div>
            )}
          </div>
        </div>
      </header>
      {error && (
        <div className="banner" role="alert">
          <div className="banner-inner">
            <Warn size={18} color="#D92D20" />
            <span style={{ flex: '1 1 320px' }}>Can't reach the SlotMind server. Retrying every 5 seconds.</span>
            <button type="button" className="btn" onClick={() => refresh()}>Retry now</button>
          </div>
        </div>
      )}
      <main className="agent-main">
        <section className="activity-page">
          <div className="pagehead">
            <div>
              <h1>Host / Agent</h1>
              <p>Review scheduling activity and updates from the background agent.</p>
            </div>
          </div>
          {data ? <ActivityFeed activity={data.activity} /> : (
            <div aria-busy="true" aria-label="Loading" className="sk" style={{ height: 240 }} />
          )}
        </section>
      </main>
    </div>
  );
}
