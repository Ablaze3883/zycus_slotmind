import { useEffect, useState } from 'react';
import { Desk } from './Desk';
import { Portal } from './Portal';
import { AgentPage } from './Agent';
import { useAuthSession, type AuthSession } from './auth';
import { LoginPage } from './Login';

function useHash() {
  const [hash, setHash] = useState(() => window.location.hash);
  useEffect(() => {
    const on = () => setHash(window.location.hash);
    window.addEventListener('hashchange', on);
    return () => window.removeEventListener('hashchange', on);
  }, []);
  return hash;
}

export default function App({ session, onLogout }: { session?: AuthSession; onLogout?: () => void } = {}) {
  const hash = useHash();
  const m = /^#\/portal\/([^/?#]+)/.exec(hash);
  if (m) return <Portal key={m[1]} id={decodeURIComponent(m[1])} onLogout={onLogout} patientMode={session?.user?.role === 'PATIENT'} />;
  if (window.location.pathname.replace(/\/+$/, '') === '/agent' && session?.user?.role !== 'PATIENT') return <AgentPage />;
  return <Desk session={session} onLogout={onLogout} />;
}

export function AuthenticatedApp() {
  const session = useAuthSession();
  if (session.loading) return <main className="auth-page"><div className="sk" style={{ width: 360, height: 300 }} /></main>;
  if (!session.user) return <LoginPage />;
  if (session.user.role === 'PATIENT' && !window.location.hash.startsWith('#/portal/')) {
    window.location.hash = session.user.appointmentId ? `#/portal/${session.user.appointmentId}` : '#/';
  }
  if (!['PATIENT', 'STAFF', 'ADMIN'].includes(session.user.role)) {
    return (
      <main className="auth-page">
        <section className="auth-card">
          <h1>Access not configured</h1>
          <p className="muted">Your identity provider did not grant a SlotMind role. Contact an administrator.</p>
          {session.error && <div role="alert" className="alert">{session.error}</div>}
          <button className="btn" type="button" onClick={() => void session.logout()}>Sign out</button>
        </section>
      </main>
    );
  }
  if (session.user.role === 'PATIENT') {
    if (!session.user.appointmentId) {
      return (
        <main className="auth-page">
          <section className="auth-card">
            <h1>No appointment is linked</h1>
            <p className="muted">Contact the clinic to link your patient account to an appointment.</p>
            <button className="btn" type="button" onClick={() => void session.logout()}>Sign out</button>
          </section>
        </main>
      );
    }
    return <Portal key={session.user.appointmentId} id={session.user.appointmentId}
      onLogout={() => { void session.logout(); }} patientMode />;
  }
  return <App session={session} onLogout={() => { void session.logout(); }} />;
}
