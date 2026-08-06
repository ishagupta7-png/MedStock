import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import * as inventoryService from "../../services/inventoryService";
import * as branchService from "../../services/branchService";
import { LoadingState, ButtonBusy } from "../../components/Spinner";

export default function AvailabilitySearchPage() {
  const navigate = useNavigate();
  const [medicineName, setMedicineName] = useState("");
  const [requiredQuantity, setRequiredQuantity] = useState("");
  const [city, setCity] = useState("");
  const [cities, setCities] = useState([]);
  // Starts true so the effect below never has to set it synchronously - the dropdown is genuinely
  // still loading on the first render.
  const [citiesLoading, setCitiesLoading] = useState(true);
  const [results, setResults] = useState([]);
  const [searchedCity, setSearchedCity] = useState("");
  const [error, setError] = useState("");
  const [searched, setSearched] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  // Offered as a list rather than free text: the backend resolves the city through branch-service,
  // so a city nobody has a branch in can only ever return nothing.
  useEffect(() => {
    let cancelled = false;
    branchService
      .getAllBranches()
      .then((branches) => {
        if (cancelled) return;
        const distinct = [...new Set(branches.map((b) => b.city).filter(Boolean))].sort();
        setCities(distinct);
      })
      .catch(() => {
        // A missing city list only costs the filter - the medicine search stays usable.
        if (!cancelled) setCities([]);
      })
      .finally(() => {
        if (!cancelled) setCitiesLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

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
      const data = await inventoryService.checkAvailability(medicineName, requiredQuantity, city);
      setResults(data);
      // Kept separately so the caption describes the search that produced these rows, rather than
      // whatever the dropdown has been changed to since.
      setSearchedCity(city);
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
          <div className="form-group">
            <label>City</label>
            <select
              value={city}
              onChange={(e) => setCity(e.target.value)}
              disabled={citiesLoading}
            >
              {citiesLoading ? (
                <option value="">Loading cities...</option>
              ) : (
                <>
                  <option value="">All cities</option>
                  {cities.map((c) => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                </>
              )}
            </select>
          </div>
          <button type="submit" className="btn-primary" disabled={isLoading}>
            {isLoading ? <ButtonBusy label="Searching..." /> : "Search"}
          </button>
        </form>

        {error && <div className="alert-error">{error}</div>}
      </div>

      {isLoading && <LoadingState label="Searching branches..." />}

      {searched && !isLoading && (
        <div className="card">
          <p className="result-caption">
            {searchedCity ? `Branches in ${searchedCity}` : "Branches nationwide"} -{" "}
            {results.length} {results.length === 1 ? "batch" : "batches"} found
          </p>
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
                    <div className="empty-state">
                      {searchedCity
                        ? `No stock found in ${searchedCity}. Try All cities.`
                        : "No stock found."}
                    </div>
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