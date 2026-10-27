import { useEffect, useState, type FormEvent } from 'react';
import { api, ApiError } from './api';
import { useAuthSession } from './auth';
import { Logo } from './icons';

export function LoginPage() {
  const { loginPatient, loginDemoRole, loginStaff, auth0Configured, error: authError } = useAuthSession();
  const [userId, setUserId] = useState('john.martinez');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [demoAuthEnabled, setDemoAuthEnabled] = useState(false);

  useEffect(() => {
    let active = true;
    api.authConfig().then((config) => {
      if (active) setDemoAuthEnabled(config.demoAuthEnabled);
    }).catch(() => {
      if (active) setDemoAuthEnabled(false);
    });
    return () => { active = false; };
  }, []);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await loginPatient(userId, password);
      const current = JSON.parse(sessionStorage.getItem('slotmind.patient.session') ?? '{}') as { user?: { appointmentId?: string } };
      window.location.hash = current.user?.appointmentId ? `#/portal/${current.user.appointmentId}` : '#/';
    } catch (e) {
      setError(e instanceof ApiError && e.status === 401 ? 'That user ID or password is not correct.' : e instanceof Error ? e.message : 'Sign-in failed.');
    } finally {
      setBusy(false);
    }
  };

  const signInDemoRole = async (role: 'STAFF' | 'ADMIN') => {
    setError(null);
    setBusy(true);
    try {
      await loginDemoRole(role);
      window.location.hash = '#/';
    } catch (e) {
      setError(e instanceof Error ? e.message : `Demo ${role.toLowerCase()} sign-in failed.`);
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="auth-page">
      <section className="auth-card" aria-labelledby="login-title">
        <div className="auth-brand">
          <div className="brand-mark"><Logo /></div>
          <div>
            <div className="auth-title">SlotMind</div>
            <div className="small muted">CareBridge appointment access</div>
          </div>
        </div>
        <h1 id="login-title">Sign in</h1>
        <p className="muted">Patients use their patient account. Clinic staff and admins use Auth0 or a local demo account.</p>

        <form className="auth-form" onSubmit={submit}>
          <div className="field">
            <label htmlFor="login-user">Patient user ID</label>
            <input id="login-user" autoComplete="username" value={userId} onChange={(event) => setUserId(event.target.value)} required />
          </div>
          <div className="field">
            <label htmlFor="login-password">Password</label>
            <input id="login-password" type="password" autoComplete="current-password" value={password}
              onChange={(event) => setPassword(event.target.value)} required />
          </div>
          {(error || authError) && <div role="alert" className="alert">{error ?? authError}</div>}
          <button type="submit" className="btn primary auth-submit" disabled={busy}>
            {busy ? 'Signing in…' : 'Sign in as patient'}
          </button>
        </form>

        <div className="auth-divider"><span>or</span></div>
        <button type="button" className="btn auth-submit" disabled={!auth0Configured} onClick={loginStaff}>
          Sign in with Auth0
        </button>
        {!auth0Configured && <p className="small muted">Staff sign-in is unavailable until Auth0 environment settings are added.</p>}

        {demoAuthEnabled && (
          <div className="auth-demo">
            <strong>Local role demo sign-in</strong>
            <span>Staff: <code>staff.demo</code> / <code>StaffDemo123!</code></span>
            <button type="button" className="btn auth-submit" disabled={busy}
              onClick={() => void signInDemoRole('STAFF')}>
              Sign in as demo staff
            </button>
            <span>Admin: <code>admin.demo</code> / <code>AdminDemo123!</code></span>
            <button type="button" className="btn auth-submit" disabled={busy}
              onClick={() => void signInDemoRole('ADMIN')}>
              Sign in as demo admin
            </button>
          </div>
        )}

        <div className="auth-demo">
          <strong>Demo patient accounts</strong>
          <span>john.martinez · emily.watson · aisha.patel · oliver.brown</span>
          <span>Shared demo password defaults to <code>PatientDemo123!</code>.</span>
          <span>Set <code>SLOTMIND_DEMO_PATIENT_PASSWORD</code> on the backend to change it.</span>
        </div>
      </section>
    </main>
  );
}
