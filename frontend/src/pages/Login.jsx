import { useContext, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { authApi, parseApiError } from "../api/api";
import { AuthContext } from "../context/AuthContext";

export default function Login() {
    const { login, authNotice, clearAuthNotice } = useContext(AuthContext);
    const navigate = useNavigate();
    const location = useLocation();

    const [form, setForm] = useState({ username: "", password: "" });
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    const handleSubmit = async (event) => {
        event.preventDefault();
        setLoading(true);
        setError("");

        try {
            const response = await authApi.post("/auth/login", form);
            login(response.data);
            navigate(location.state?.from || "/", { replace: true });
        } catch (requestError) {
            setError(parseApiError(requestError, "Unable to sign in"));
        } finally {
            setLoading(false);
        }
    };

    return (
        <section className="auth-layout">
            <article className="auth-card auth-card-featured">
                <p className="eyebrow">Welcome Back</p>
                <h1>Sign In to the auction floor</h1>
                <p className="auth-lead">
                    Access secure bidding, seller actions, live updates, and winner visibility in
                    real time.
                </p>
                <div className="feature-list">
                    <div className="feature-pill">JWT-protected bidding</div>
                    <div className="feature-pill">Live auction updates</div>
                    <div className="feature-pill">Server-side winner resolution</div>
                </div>
            </article>

            <section className="auth-card">
                <div className="section-heading">
                    <h2>Sign In</h2>
                    <p>Use your registered username and password to access auctions.</p>
                </div>

                <form className="form-stack" onSubmit={handleSubmit}>
                    <label className="form-field">
                        <span>Username</span>
                        <input
                            className="form-input"
                            value={form.username}
                            onChange={(event) =>
                                setForm((previous) => ({
                                    ...previous,
                                    username: event.target.value,
                                }))
                            }
                            onFocus={clearAuthNotice}
                            placeholder="username"
                        />
                    </label>

                    <label className="form-field">
                        <span>Password</span>
                        <input
                            className="form-input"
                            type="password"
                            value={form.password}
                            onChange={(event) =>
                                setForm((previous) => ({
                                    ...previous,
                                    password: event.target.value,
                                }))
                            }
                            onFocus={clearAuthNotice}
                            placeholder="password"
                        />
                    </label>

                    {authNotice ? <p className="status-warning">{authNotice}</p> : null}
                    {error ? <p className="status-error">{error}</p> : null}

                    <button className="primary-button" disabled={loading} type="submit">
                        {loading ? "Signing in..." : "Sign In"}
                    </button>
                </form>

                <p className="auth-footer">
                    Need an account? <Link to="/register">Register here</Link>.
                </p>
            </section>
        </section>
    );
}
