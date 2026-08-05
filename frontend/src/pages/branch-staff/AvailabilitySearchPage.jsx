import { useState } from "react";
import { useNavigate } from "react-router-dom";
import * as inventoryService from "../../services/inventoryService";

export default function AvailabilitySearchPage() {
  const navigate = useNavigate();
  const [medicineName, setMedicineName] = useState("");
  const [requiredQuantity, setRequiredQuantity] = useState("");
  const [results, setResults] = useState([]);
  const [error, setError] = useState("");
  const [searched, setSearched] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  const handleRaiseRequest = (branch) => {
    navigate("/raise-request", {
      state: {
        medicineName,
        quantity: requiredQuantity,
        targetBranchId: branch.branchId,
        targetBranchName: branch.branchName,
      },
    });
  };

  const handleSearch = async (e) => {
    e.preventDefault();
    setError("");
    setIsLoading(true);
    try {
      const data = await inventoryService.checkAvailability(medicineName, requiredQuantity);
      setResults(data);
      setSearched(true);
    } catch (err) {
      setError(err.response?.data?.message || "Search failed.");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">Availability Search</h1>
      </div>

      <div className="card">
        <form onSubmit={handleSearch} className="form-grid">
          <div className="form-group">
            <label>Medicine Name</label>
            <input
              value={medicineName}
              onChange={(e) => setMedicineName(e.target.value)}
              required
            />
          </div>
          <div className="form-group">
            <label>Required Quantity</label>
            <input
              type="number"
              min="1"
              value={requiredQuantity}
              onChange={(e) => setRequiredQuantity(e.target.value)}
              required
            />
          </div>
          <button type="submit" className="btn-primary">
            Search
          </button>
        </form>

        {error && <div className="alert-error">{error}</div>}
      </div>

      {isLoading && <div className="loading-text">Loading...</div>}

      {searched && !isLoading && (
        <div className="card">
          <table>
            <thead>
              <tr>
                <th>Branch</th>
                <th>Quantity</th>
                <th>Expiry Date</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {results.length === 0 ? (
                <tr>
                  <td colSpan="4">
                    <div className="empty-state">No stock found.</div>
                  </td>
                </tr>
              ) : (
                results.map((r) => (
                  <tr key={r.id}>
                    <td>{r.branchName}</td>
                    <td>{r.quantity}</td>
                    <td>{r.expiryDate}</td>
                    <td>
                      <button
                        type="button"
                        className="btn-secondary"
                        onClick={() => handleRaiseRequest(r)}
                      >
                        Raise Request
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}