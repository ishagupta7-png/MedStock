import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import * as authService from "../services/authService";
import { ButtonBusy } from "../components/Spinner";

const ROLES = ["BRANCH_STAFF", "INVENTORY_MANAGER", "WAREHOUSE_ADMIN"];

export default function RegisterPage() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState(ROLES[0]);
  const [branchId, setBranchId] = useState("");
  const [warehouseCode, setWarehouseCode] = useState("");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setSuccess("");

    const payload = { username, password, role };
    if (role === "BRANCH_STAFF" || role === "INVENTORY_MANAGER") {
      payload.branchId = Number(branchId);
    } else if (role === "WAREHOUSE_ADMIN") {
      payload.warehouseCode = warehouseCode;
    }

    setIsLoading(true);
    try {
      await authService.register(payload);
      setSuccess("Registration successful! Redirecting to login...");
      setTimeout(() => navigate("/login"), 1500);
    } catch (err) {
      setError(err.response?.data?.message || "Registration failed.");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="auth-wrapper">
      <div className="auth-card">
        <div className="auth-logo">M</div>
        <h1 className="auth-title">MedStock</h1>
        <p className="auth-subtitle">Create an account to get started</p>

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
          <div className="form-group">
            <label>Role</label>
            <select value={role} onChange={(e) => setRole(e.target.value)}>
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {r}
                </option>
              ))}
            </select>
          </div>

          {(role === "BRANCH_STAFF" || role === "INVENTORY_MANAGER") && (
            <div className="form-group">
              <label>Branch ID</label>
              <input
                type="number"
                value={branchId}
                onChange={(e) => setBranchId(e.target.value)}
                required
              />
            </div>
          )}

          {role === "WAREHOUSE_ADMIN" && (
            <div className="form-group">
              <label>Warehouse Access Code</label>
              <input
                value={warehouseCode}
                onChange={(e) => setWarehouseCode(e.target.value)}
                required
              />
            </div>
          )}

          {error && <div className="alert-error">{error}</div>}
          {success && <div className="alert-success">{success}</div>}
          {/* Stays disabled through the post-success redirect delay, so the form cannot be
              submitted a second time while the navigation is pending. */}
          <button type="submit" className="btn-primary" disabled={isLoading || !!success}>
            {isLoading ? <ButtonBusy label="Registering..." /> : "Register"}
          </button>
        </form>

        <p className="auth-footer">
          Already have an account? <Link to="/login">Login</Link>
        </p>
      </div>
    </div>
  );
}