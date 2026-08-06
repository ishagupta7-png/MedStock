import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { ButtonBusy } from "../components/Spinner";

const REDIRECT_BY_ROLE = {
  ADMIN: "/admin/branches",
  BRANCH_STAFF: "/availability",
  INVENTORY_MANAGER: "/stock",
  WAREHOUSE_ADMIN: "/escalated",
};

export default function LoginPage() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setIsLoading(true);
    try {
      const role = await login(username, password);
      navigate(REDIRECT_BY_ROLE[role] || "/login");
    } catch (err) {
      setError(err.response?.data?.message || "Login failed. Check your credentials.");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="auth-wrapper">
      <div className="auth-card">
        <div className="auth-logo">M</div>
        <h1 className="auth-title">MedStock</h1>
        <p className="auth-subtitle">Sign in to manage inter-branch medicine transfers</p>

        <form onSubmit={handleSubmit} className="auth-form">
          <div className="form-group">
            <label>Username</label>
            <input value={username} onChange={(e) => setUsername(e.target.value)} required />
          </div>
          <div className="form-group">
            <label>Password</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          {error && <div className="alert-error">{error}</div>}
          <button type="submit" className="btn-primary" disabled={isLoading}>
            {isLoading ? <ButtonBusy label="Signing in..." /> : "Login"}
          </button>
        </form>

        <p className="auth-footer">
          Don&apos;t have an account? <Link to="/register">Register</Link>
        </p>
      </div>
    </div>
  );
}