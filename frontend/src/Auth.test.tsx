import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { AuthProvider } from './auth';
import { AuthenticatedApp } from './App';
import { LoginPage } from './Login';
import { routeFetch } from './test/fixtures';

beforeEach(() => {
  sessionStorage.clear();
  window.location.hash = '';
  window.history.pushState({}, '', '/');
});

describe('patient authentication', () => {
  it('offers and stores a demo staff session when demo roles are enabled', async () => {
    const fetch = routeFetch({
      'GET /auth/config': { auth0Configured: false, demoAuthEnabled: true, patientAccounts: [] },
      'POST /auth/demo/login': {
        accessToken: 'demo_staff_session',
        expiresAt: new Date(Date.now() + 60_000).toISOString(),
        user: { userId: 'staff.demo', role: 'STAFF', patientName: '' },
      },
    });
    const user = userEvent.setup();
    render(<AuthProvider><LoginPage /></AuthProvider>);

    await user.click(await screen.findByRole('button', { name: 'Sign in as demo staff' }));

    await waitFor(() => {
      const session = JSON.parse(sessionStorage.getItem('slotmind.patient.session') ?? '{}') as {
        token?: string; user?: { role?: string };
      };
      expect(session.token).toBe('demo_staff_session');
      expect(session.user?.role).toBe('STAFF');
    });
    const loginCall = fetch.mock.calls.find(([url]) => String(url) === '/api/auth/demo/login');
    expect(JSON.parse(String(loginCall?.[1]?.body))).toEqual({
      userId: 'staff.demo', password: 'StaffDemo123!',
    });
  });

  it('signs in a seeded patient and opens only their appointment portal', async () => {
    const fetch = routeFetch({
      'POST /auth/patient/login': {
        accessToken: 'pt_demo_session',
        expiresAt: new Date(Date.now() + 60_000).toISOString(),
        user: { userId: 'john.martinez', role: 'PATIENT', patientName: 'John Martinez', appointmentId: 'APT-001' },
      },
      'GET /portal/appointments/APT-001': {
        appointment: {
          id: 'APT-001', patientName: 'John Martinez', type: 'ROUTINE', status: 'CONFIRMED',
          providerId: 'PRV-001', providerName: 'Dr. Sarah Chen',
          slot: { id: 'SLT-001', startTime: '2030-01-15T11:00:00', durationMinutes: 30, status: 'BOOKED',
            providerId: 'PRV-001', providerName: 'Dr. Sarah Chen' },
        },
        suggestions: [],
      },
      'POST /auth/patient/logout': { status: 'SIGNED_OUT' },
    });
    const user = userEvent.setup();
    render(<AuthProvider><AuthenticatedApp /></AuthProvider>);

    await user.type(await screen.findByLabelText('Password'), 'PatientDemo123!');
    await user.click(screen.getByRole('button', { name: 'Sign in as patient' }));

    expect(await screen.findByRole('heading', { name: 'Hi John' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeInTheDocument();
    await waitFor(() => expect(window.location.hash).toBe('#/portal/APT-001'));
    const portalRequest = fetch.mock.calls.find(([url]) => String(url) === '/api/portal/appointments/APT-001');
    expect(portalRequest?.[1]?.headers).toMatchObject({ Authorization: 'Bearer pt_demo_session' });
    await user.click(screen.getByRole('button', { name: 'Sign out' }));
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('shows a clear error for invalid patient credentials', async () => {
    routeFetch({
      'POST /auth/patient/login': new Response(JSON.stringify({
        error: 'INVALID_CREDENTIALS', message: 'User ID or password is incorrect.',
      }), { status: 401, headers: { 'Content-Type': 'application/json' } }),
    });
    const user = userEvent.setup();
    render(<AuthProvider><AuthenticatedApp /></AuthProvider>);

    await user.type(await screen.findByLabelText('Password'), 'wrong');
    await user.click(screen.getByRole('button', { name: 'Sign in as patient' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('That user ID or password is not correct.');
  });
});
