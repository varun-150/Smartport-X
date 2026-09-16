import { useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../lib/authContext';
import { ROUTES } from '../lib/routes';
import { Button } from '../components/ui';
import { TextInput } from '../components/form';
import { ErrorBanner } from '../components/feedback';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(username.trim(), password);
      navigate(from && from !== ROUTES.login ? from : ROUTES.dashboard, { replace: true });
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="login-screen">
      <form className="login-card" onSubmit={handleSubmit}>
        <div className="login-brand">
          <span className="brand-mark">SPX</span>
          <div>
            <h1 className="login-title">SmartPortX</h1>
            <p className="login-subtitle">Terminal Operations Console</p>
          </div>
        </div>

        {error ? <div style={{ marginBottom: 14 }}><ErrorBanner error={error} /></div> : null}

        <TextInput
          label="Username"
          autoComplete="username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          placeholder="operator"
          required
          autoFocus
        />
        <TextInput
          label="Password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="••••••••"
          required
        />

        <div className="form-actions">
          <Button type="submit" variant="primary" loading={submitting}>
            Sign in
          </Button>
        </div>
      </form>
    </div>
  );
}