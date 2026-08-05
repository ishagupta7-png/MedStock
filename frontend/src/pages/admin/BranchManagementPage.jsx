import { useEffect, useState } from "react";
import * as branchService from "../../services/branchService";

export default function BranchManagementPage() {
  const [branches, setBranches] = useState([]);
  const [branchName, setBranchName] = useState("");
  const [city, setCity] = useState("");
  const [contactNumber, setContactNumber] = useState("");
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(false);

  const loadBranches = async () => {
    setIsLoading(true);
    try {
      const data = await branchService.getAllBranches();
      setBranches(data);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to load branches.");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadBranches();
  }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    try {
      await branchService.createBranch({ branchName, city, contactNumber });
      setBranchName("");
      setCity("");
      setContactNumber("");
      loadBranches();
    } catch (err) {
      setError(err.response?.data?.message || "Failed to create branch.");
    }
  };

  const handleDelete = async (id) => {
    try {
      await branchService.deleteBranch(id);
      loadBranches();
    } catch (err) {
      setError(err.response?.data?.message || "Failed to delete branch.");
    }
  };

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">Branch Management</h1>
      </div>

      <div className="card">
        <form onSubmit={handleSubmit} className="form-grid">
          <div className="form-group">
            <label>Branch Name</label>
            <input value={branchName} onChange={(e) => setBranchName(e.target.value)} required />
          </div>
          <div className="form-group">
            <label>City</label>
            <input value={city} onChange={(e) => setCity(e.target.value)} required />
          </div>
          <div className="form-group">
            <label>Contact Number</label>
            <input
              value={contactNumber}
              onChange={(e) => setContactNumber(e.target.value)}
              required
            />
          </div>
          <button type="submit" className="btn-primary">
            Add Branch
          </button>
        </form>

        {error && <div className="alert-error">{error}</div>}
      </div>

      <div className="card">
        {isLoading ? (
          <div className="loading-text">Loading...</div>
        ) : (
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Branch Name</th>
                <th>City</th>
                <th>Contact Number</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {branches.length === 0 ? (
                <tr>
                  <td colSpan="5">
                    <div className="empty-state">No branches found.</div>
                  </td>
                </tr>
              ) : (
                branches.map((b) => (
                  <tr key={b.id}>
                    <td>{b.id}</td>
                    <td>{b.branchName}</td>
                    <td>{b.city}</td>
                    <td>{b.contactNumber}</td>
                    <td>
                      <button
                        type="button"
                        className="btn-danger"
                        onClick={() => handleDelete(b.id)}
                      >
                        Delete
                      </button>
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