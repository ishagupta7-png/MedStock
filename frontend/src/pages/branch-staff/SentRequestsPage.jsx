import { useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";
import * as transferService from "../../services/transferService";
import { LoadingState, ButtonBusy } from "../../components/Spinner";

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
  const [deletingId, setDeletingId] = useState(null);

  const loadRequests = async () => {
    setIsLoading(true);
    try {
      // Filtered server-side. This page used to fetch both directions and drop the incoming ones
      // client-side, which is only ever as correct as the caller remembering to filter.
      const data = await transferService.getSentRequestsForBranch(branchId);
      setRequests(data);
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
    // Marked busy rather than removed optimistically: the server can legitimately refuse this
    // (a CONFIRMED request, or a branch that does not own it), and a row that vanishes and then
    // reappears alongside an error reads as a bug rather than as a refusal.
    setDeletingId(id);
    try {
      await transferService.deleteRequest(id);
      await loadRequests();
    } catch (err) {
      setError(err.response?.data?.message || "Failed to delete request.");
    } finally {
      setDeletingId(null);
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
          <LoadingState />
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
                          disabled={deletingId === r.id}
                          onClick={() => handleDelete(r.id)}
                        >
                          {deletingId === r.id ? (
                            <ButtonBusy label="Deleting..." tone="inherit" />
                          ) : (
                            "Delete"
                          )}
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