import { useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";
import * as transferService from "../../services/transferService";

export default function IncomingRequestsPage() {
  const { branchId } = useAuth();
  const [requests, setRequests] = useState([]);
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(false);

  const loadRequests = async () => {
    setIsLoading(true);
    setError("");
    try {
      // The backend decides what this branch may see and act on: pending requests from other
      // branches, minus any this branch already declined, each flagged with whether escalation
      // currently points here and whether this branch actually holds the stock.
      const data = await transferService.getOpenRequestsForBranch(branchId);
      setRequests(data);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to load incoming requests.");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadRequests();
  }, [branchId]);

  const act = async (fn, id, fallbackMessage) => {
    setError("");
    try {
      await fn(id);
    } catch (err) {
      setError(err.response?.data?.message || fallbackMessage);
    }
    // Reload either way: on conflict the row has already changed and the list must catch up.
    loadRequests();
  };

  const handleApprove = (id) =>
    act(transferService.approveRequest, id, "Failed to approve request.");

  const handleReject = (id) =>
    act(transferService.rejectRequest, id, "Failed to decline request.");

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">Incoming Requests</h1>
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
                <th>Requesting Branch</th>
                <th>Contact</th>
                <th>Criticality</th>
                <th>Priority</th>
                <th>Your Stock</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {requests.length === 0 ? (
                <tr>
                  <td colSpan="9">
                    <div className="empty-state">No open requests for your branch right now.</div>
                  </td>
                </tr>
              ) : (
                requests.map((r) => (
                  <tr key={r.id}>
                    <td>{r.id}</td>
                    <td>{r.medicineName}</td>
                    <td>{r.quantity}</td>
                    <td>{r.requestingBranchName}</td>
                    <td>{r.requestingBranchContact || "-"}</td>
                    <td>
                      <span className={`badge badge-${(r.criticality || "routine").toLowerCase()}`}>
                        {r.criticality || "-"}
                      </span>
                    </td>
                    <td>
                      {r.assignedToYou ? (
                        <span className="badge badge-pending">Assigned to you</span>
                      ) : (
                        <span className="badge badge-routine">
                          Open{r.currentTargetBranchName ? ` - with ${r.currentTargetBranchName}` : ""}
                        </span>
                      )}
                    </td>
                    <td>
                      {r.canFulfil ? (
                        <span className="badge badge-confirmed">Enough stock</span>
                      ) : (
                        <span className="badge badge-cancelled">Not enough</span>
                      )}
                    </td>
                    <td>
                      <div className="btn-row">
                        <button
                          type="button"
                          className="btn-primary"
                          disabled={!r.canFulfil}
                          title={
                            r.canFulfil
                              ? "Supply this request from your branch's stock"
                              : "Your branch does not have enough unexpired stock in a single batch"
                          }
                          onClick={() => handleApprove(r.id)}
                        >
                          Approve
                        </button>
                        <button
                          type="button"
                          className="btn-danger"
                          title="Decline - your branch will not be asked for this request again"
                          onClick={() => handleReject(r.id)}
                        >
                          Decline
                        </button>
                      </div>
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
