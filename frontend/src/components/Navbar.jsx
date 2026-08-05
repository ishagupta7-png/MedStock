import { NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

const LINKS_BY_ROLE = {
  ADMIN: [
    { to: "/admin/branches", label: "Branches" },
    { to: "/admin/warehouse-codes", label: "Warehouse Codes" },
  ],
  BRANCH_STAFF: [
    { to: "/availability", label: "Availability" },
    { to: "/raise-request", label: "Raise Request" },
    { to: "/incoming-requests", label: "Incoming Requests" },
    { to: "/sent-requests", label: "My Requests" },
  ],
  INVENTORY_MANAGER: [
    { to: "/stock", label: "Stock" },
    { to: "/alerts", label: "Alerts" },
  ],
  WAREHOUSE_ADMIN: [{ to: "/escalated", label: "Escalated Requests" }],
};

const ROLE_LABEL = {
  ADMIN: "Admin",
  BRANCH_STAFF: "Branch Staff",
  INVENTORY_MANAGER: "Inventory Manager",
  WAREHOUSE_ADMIN: "Warehouse Admin",
};

export default function Navbar() {
  const { role, username, branchId, branchName, logout } = useAuth();
  const navigate = useNavigate();

  const links = LINKS_BY_ROLE[role] || [];

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  // The branch id is shown alongside the name on purpose: two accounts can sit on the same
  // branch, and the id is what every incoming/outgoing request is actually matched on.
  const branchLabel = branchId
    ? `${branchName || "Branch"} (#${branchId})`
    : "No branch";

  return (
    <nav className="navbar">
      <div className="navbar-brand">
        <span className="navbar-logo">M</span>
        <span className="navbar-title">MedStock</span>
        {username && (
          <span className="navbar-user">
            <strong className="navbar-user-name">{username}</strong>
            <span className="navbar-chip">{ROLE_LABEL[role] || role}</span>
            <span className="navbar-chip">{branchLabel}</span>
          </span>
        )}
      </div>
      <div className="navbar-links">
        {links.map((link) => (
          <NavLink
            key={link.to}
            to={link.to}
            className={({ isActive }) => (isActive ? "active" : undefined)}
          >
            {link.label}
          </NavLink>
        ))}
      </div>
      <button type="button" onClick={handleLogout} className="btn-logout">
        Logout
      </button>
    </nav>
  );
}