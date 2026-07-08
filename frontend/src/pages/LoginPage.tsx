import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Form, Input, Button, Alert } from 'antd';
import { MailOutlined, LockOutlined } from '@ant-design/icons';
import { useAuth } from '@/context/AuthContext';
import AppLogo from '@/components/AppLogo';
import { HEADING_FONT } from '@/theme/antdTheme';

export default function LoginPage() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = useCallback(
    async (values: { username: string; password: string }) => {
      setLoading(true);
      setError(null);
      try {
        await login(values.username, values.password);
        navigate('/dashboard', { replace: true });
      } catch {
        setError('Incorrect email or password.');
      } finally {
        setLoading(false);
      }
    },
    [login, navigate],
  );

  return (
    <div style={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      {/* ── Left brand panel — Black #232323, per ADR-013 ── */}
      <div
        style={{
          width: '44%',
          flexShrink: 0,
          background: '#232323',
          padding: '48px 52px',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between',
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        {/* Subtle ring texture */}
        <svg
          aria-hidden="true"
          style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', zIndex: 0, pointerEvents: 'none' }}
          xmlns="http://www.w3.org/2000/svg"
        >
          <defs>
            <pattern id="rings" width="118" height="118" patternUnits="userSpaceOnUse">
              <g fill="none" stroke="#ffffff" strokeOpacity="0.05" strokeWidth="1.3">
                <circle cx="0"   cy="0"   r="59" />
                <circle cx="118" cy="0"   r="59" />
                <circle cx="0"   cy="118" r="59" />
                <circle cx="118" cy="118" r="59" />
                <circle cx="59"  cy="59"  r="59" />
              </g>
              <circle cx="59" cy="59" r="59" fill="none" stroke="#f05756" strokeOpacity="0.07" strokeWidth="1.3" />
            </pattern>
          </defs>
          <rect width="100%" height="100%" fill="url(#rings)" />
        </svg>

        {/* Logo — dark variant: gradient glyph + white wordmark */}
        <div style={{ position: 'relative', zIndex: 1 }}>
          <AppLogo variant="dark" height={30} />
        </div>

        {/* Tagline */}
        <div style={{ position: 'relative', zIndex: 1 }}>
          <h2
            style={{
              fontFamily: HEADING_FONT,
              fontWeight: 700,
              fontSize: 'clamp(26px, 3vw, 36px)',
              lineHeight: 1.18,
              letterSpacing: '-0.015em',
              color: '#ffffff',
              margin: '0 0 16px',
            }}
          >
            Plan with<br />confidence.
          </h2>
          <p
            style={{
              fontFamily: "'Lato', system-ui, sans-serif",
              fontSize: 15,
              lineHeight: 1.65,
              color: 'rgba(255,255,255,0.8)',
              margin: 0,
              maxWidth: 320,
            }}
          >
            Headcount, budget and runway &mdash; one trusted source of truth for the finance team.
          </p>
        </div>

        <span
          style={{
            position: 'relative',
            zIndex: 1,
            fontFamily: "'Lato', system-ui, sans-serif",
            fontSize: 11,
            letterSpacing: '0.1em',
            textTransform: 'uppercase',
            color: 'rgba(255,255,255,0.45)',
          }}
        >
          Cognologix Technologies
        </span>
      </div>

      {/* ── Right form panel — white ── */}
      <div
        style={{
          flex: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '48px 64px',
          background: '#ffffff',
        }}
      >
        <div style={{ width: '100%', maxWidth: 360 }}>
          <h1
            style={{
              fontFamily: HEADING_FONT,
              fontWeight: 700,
              fontSize: 26,
              letterSpacing: '-0.012em',
              color: '#232323',
              margin: '0 0 6px',
            }}
          >
            Sign in
          </h1>
          <p style={{ fontSize: 14, color: '#888888', margin: '0 0 28px' }}>
            Welcome back. Enter your details to continue.
          </p>

          {error && (
            <Alert
              type="error"
              message={error}
              showIcon
              style={{ marginBottom: 20 }}
            />
          )}

          <Form layout="vertical" onFinish={handleSubmit} requiredMark={false}>
            <Form.Item
              label={<span style={{ fontWeight: 700, color: '#555555', fontSize: 13 }}>Email</span>}
              name="username"
              rules={[{ required: true, message: 'Enter your email.' }]}
            >
              <Input
                prefix={<MailOutlined style={{ color: '#888888' }} />}
                placeholder="you@cognologix.com"
                size="large"
                type="email"
                autoComplete="username"
              />
            </Form.Item>

            <Form.Item
              label={<span style={{ fontWeight: 700, color: '#555555', fontSize: 13 }}>Password</span>}
              name="password"
              rules={[{ required: true, message: 'Enter your password.' }]}
            >
              <Input.Password
                prefix={<LockOutlined style={{ color: '#888888' }} />}
                placeholder="Enter your password"
                size="large"
                autoComplete="current-password"
              />
            </Form.Item>

            <div style={{ textAlign: 'right', marginTop: -8, marginBottom: 24 }}>
              <a href="#" style={{ fontSize: 13, color: '#f05756' }}>
                Forgot password?
              </a>
            </div>

            <Form.Item style={{ marginBottom: 0 }}>
              <Button
                type="primary"
                htmlType="submit"
                size="large"
                block
                loading={loading}
                style={{
                  fontFamily: HEADING_FONT,
                  fontWeight: 600,
                  height: 44,
                  fontSize: 15,
                }}
              >
                Sign in
              </Button>
            </Form.Item>
          </Form>

          <p
            style={{
              fontSize: 12,
              color: '#888888',
              textAlign: 'center',
              marginTop: 20,
              lineHeight: 1.5,
            }}
          >
            Invite-only access. Contact your administrator if you need an account.
          </p>
        </div>
      </div>
    </div>
  );
}
