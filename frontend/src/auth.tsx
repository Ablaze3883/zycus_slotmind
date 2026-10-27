import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { Auth0Provider, useAuth0 } from '@auth0/auth0-react';
import { api, setAccessTokenProvider, type AuthUser } from './api';

export interface AuthSession {
  user: AuthUser | null;
  loading: boolean;
  auth0Configured: boolean;
  error: string | null;
  loginPatient: (userId: string, password: string) => Promise<void>;
  loginDemoRole: (role: 'STAFF' | 'ADMIN') => Promise<void>;
  loginStaff: () => void;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthSession | null>(null);

const auth0Domain = import.meta.env.VITE_AUTH0_DOMAIN ?? '';
const auth0ClientId = import.meta.env.VITE_AUTH0_CLIENT_ID ?? '';
const auth0Audience = import.meta.env.VITE_AUTH0_AUDIENCE ?? '';
const rolesClaim = import.meta.env.VITE_AUTH0_ROLES_CLAIM ?? 'https://slotmind.dev/roles';
const hasAuth0Config = Boolean(auth0Domain && auth0ClientId && auth0Audience);
const SESSION_KEY = 'slotmind.patient.session';
const DEMO_ROLE_CREDENTIALS = {
  STAFF: { userId: 'staff.demo', password: import.meta.env.VITE_DEMO_STAFF_PASSWORD || 'StaffDemo123!' },
  ADMIN: { userId: 'admin.demo', password: import.meta.env.VITE_DEMO_ADMIN_PASSWORD || 'AdminDemo123!' },
} as const;

function saveSession(result: { accessToken: string; expiresAt: string; user: AuthUser }) {
  const next = {
    token: result.accessToken,
    user: result.user,
    expiresAt: Date.parse(result.expiresAt),
  };
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(next));
  return next;
}

function readPatientSession(): { token: string; user: AuthUser; expiresAt: number } | null {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY);
    if (!raw) return null;
    const session = JSON.parse(raw) as { token: string; user: AuthUser; expiresAt: number };
    if (session.expiresAt <= Date.now()) {
      sessionStorage.removeItem(SESSION_KEY);
      return null;
    }
    return session;
  } catch {
    sessionStorage.removeItem(SESSION_KEY);
    return null;
  }
}

function LocalPatientSession({ children }: { children: ReactNode }) {
  const [patientSession, setPatientSession] = useState(readPatientSession);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setAccessTokenProvider(async () => readPatientSession()?.token ?? null);
    return () => setAccessTokenProvider(null);
  }, []);

  const value = useMemo<AuthSession>(() => ({
    user: patientSession?.user ?? null,
    loading: false,
    auth0Configured: false,
    error,
    async loginPatient(userId, password) {
      setError(null);
      const result = await api.patientLogin(userId, password);
      setPatientSession(saveSession(result));
    },
    async loginDemoRole(role) {
      setError(null);
      const credentials = DEMO_ROLE_CREDENTIALS[role];
      const result = await api.demoRoleLogin(credentials.userId, credentials.password);
      setPatientSession(saveSession(result));
    },
    loginStaff() {
      setError('Configure Auth0 or use a demo role account.');
    },
    async logout() {
      try {
        await api.patientLogout();
      } catch {
        setError('You are signed out on this device, but the server could not revoke the session. It expires automatically.');
      } finally {
        sessionStorage.removeItem(SESSION_KEY);
        setPatientSession(null);
      }
    },
  }), [patientSession, error]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

function tokenRoles(token: string): string[] {
  try {
    const payload = token.split('.')[1];
    if (!payload) return [];
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const normalized = base64.padEnd(base64.length + ((4 - base64.length % 4) % 4), '=');
    const binary = atob(normalized);
    const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
    const claims = JSON.parse(new TextDecoder().decode(bytes)) as Record<string, unknown>;
    const value = claims[rolesClaim];
    if (typeof value === 'string') return [value.toUpperCase().replace(/^ROLE_/, '')];
    if (Array.isArray(value)) return value.filter((role): role is string => typeof role === 'string').map((role) => role.toUpperCase().replace(/^ROLE_/, ''));
    return [];
  } catch {
    return [];
  }
}

function Auth0Session({ children }: { children: ReactNode }) {
  const { isLoading, isAuthenticated, user, getAccessTokenSilently, loginWithRedirect, logout, error: auth0Error } = useAuth0();
  const [patientSession, setPatientSession] = useState(readPatientSession);
  const [token, setToken] = useState<string | null>(null);
  const [tokenError, setTokenError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    if (!isAuthenticated) {
      setToken(null);
      setTokenError(null);
      return () => { active = false; };
    }
    getAccessTokenSilently({ authorizationParams: { audience: auth0Audience } })
      .then((accessToken) => {
        if (active) {
          setToken(accessToken ?? null);
          setTokenError(null);
        }
      })
      .catch(() => {
        if (active) setTokenError('Could not get an access token for the SlotMind API. Check the Auth0 API audience.');
      });
    return () => { active = false; };
  }, [getAccessTokenSilently, isAuthenticated]);

  useEffect(() => {
    setAccessTokenProvider(async () => {
      const local = readPatientSession();
      if (local) return local.token;
      if (!isAuthenticated) return null;
      return (await getAccessTokenSilently({ authorizationParams: { audience: auth0Audience } })) ?? null;
    });
    return () => setAccessTokenProvider(null);
  }, [getAccessTokenSilently, isAuthenticated]);

  const roles = token ? tokenRoles(token) : [];
  const role = roles.find((candidate) => candidate === 'ADMIN' || candidate === 'STAFF');
  const auth0User: AuthUser | null = isAuthenticated && role
    ? { userId: user?.email ?? user?.sub ?? 'Auth0 user', role, patientName: user?.name ?? '' }
    : null;
  const sessionUser = patientSession?.user ?? auth0User;

  const value = useMemo<AuthSession>(() => ({
    user: sessionUser,
    loading: isLoading || (isAuthenticated && !token && !tokenError),
    auth0Configured: true,
    error: tokenError ?? (isAuthenticated && token && !role ? `Your Auth0 account needs a ${rolesClaim} role claim (STAFF or ADMIN).` : auth0Error?.message ?? null),
    async loginPatient(userId, password) {
      const result = await api.patientLogin(userId, password);
      setPatientSession(saveSession(result));
    },
    async loginDemoRole(role) {
      const credentials = DEMO_ROLE_CREDENTIALS[role];
      const result = await api.demoRoleLogin(credentials.userId, credentials.password);
      setPatientSession(saveSession(result));
    },
    loginStaff: () => { void loginWithRedirect({ authorizationParams: { audience: auth0Audience } }); },
    async logout() {
      if (patientSession) {
        try {
          await api.patientLogout();
        } catch {
          setTokenError('You are signed out on this device, but the server could not revoke the session. It expires automatically.');
        } finally {
          sessionStorage.removeItem(SESSION_KEY);
          setPatientSession(null);
        }
        return;
      }
      logout({ logoutParams: { returnTo: window.location.origin } });
    },
  }), [sessionUser, patientSession, isLoading, isAuthenticated, token, tokenError, role, auth0Error, loginWithRedirect, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  if (!hasAuth0Config) return <LocalPatientSession>{children}</LocalPatientSession>;
  return (
    <Auth0Provider
      domain={auth0Domain}
      clientId={auth0ClientId}
      authorizationParams={{
        redirect_uri: window.location.origin,
        audience: auth0Audience,
        scope: 'openid profile email',
      }}
      cacheLocation="memory"
    >
      <Auth0Session>{children}</Auth0Session>
    </Auth0Provider>
  );
}

export function useAuthSession() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuthSession must be used within AuthProvider.');
  return context;
}
