import { useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";
import * as transferService from "../../services/transferService";

const APPROVAL_LABEL = {
  PENDING: "Awaiting response",
  CONFIRMED: "Approved",
  REJECTED: "Not approved",
  ESCALATED_TO_WAREHOUSE: "Escalated to warehouse",
  CANCELLED: "Cancelled",
};

export default function SentRequestsPage() {
  const { branchId } = useAuth();
  const [requests, setRequests] = useState([]);
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(false);

  const loadRequests = async () => {
    setIsLoading(true);
    try {
      const data = await transferService.getRequestsByBranch(branchId);
      const sent = data.filter((r) => Number(r.requestingBranchId) === Number(branchId));
      setRequests(sent);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to load your requests.");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadRequests();
  }, [branchId]);

  const handleDelete = async (id) => {
    setError("");
    // Drop the row straight away so the click feels immediate, then reconcile with the server.
    const previous = requests;
    setRequests((current) => current.filter((r) => r.id !== id));
    try {
      await transferService.deleteRequest(id);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to delete request.");
      setRequests(previous); // put it back - the delete did not happen
    }
  };

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">My Requests</h1>
      </div>

      {error && <div className="alert-error">{error}</div>}

      <div className="card">
        {isLoading ? (
          <div className="loading-text">Loading...</div>
        ) : (
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Medicine</th>
                <th>Quantity</th>
                <th>Current Branch</th>
                <th>Contact</th>
                <th>Criticality</th>
                <th>Approval Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {requests.length === 0 ? (
                <tr>
                  <td colSpan="8">
                    <div className="empty-state">You haven't raised any requests yet.</div>
                  </td>
                </tr>
              ) : (
                requests.map((r) => (
                  <tr key={r.id}>
                    <td>{r.id}</td>
                    <td>{r.medicineName}</td>
                    <td>{r.quantity}</td>
                    {/* Once confirmed, the branch that actually supplied the stock is what
                        matters - any branch with stock can approve, not just the escalation
                        target. */}
                    <td>
                      {r.fulfilledByBranchName ||
                        r.currentTargetBranchName ||
                        "Central Warehouse"}
                    </td>
                    <td>{r.currentTargetBranchContact || "-"}</td>
                    <td>
                      <span className={`badge badge-${r.criticality.toLowerCase()}`}>
                        {r.criticality}
                      </span>
                    </td>
                    <td>
                      <span className={`badge badge-${r.status.toLowerCase()}`}>
                        {APPROVAL_LABEL[r.status] || r.status}
                      </span>
                    </td>
                    <td>
                      {r.status !== "CONFIRMED" && (
                        <button
                          type="button"
                          className="btn-secondary"
                          onClick={() => handleDelete(r.id)}
                        >
                          Delete
                        </button>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}