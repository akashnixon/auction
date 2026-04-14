import { useContext, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { authApi, parseApiError, userApi } from "../api/api";
import { AuthContext } from "../context/AuthContext";

export default function Register() {
    const { login, authNotice, clearAuthNotice } = useContext(AuthContext);
    const navigate = useNavigate();

    const [form, setForm] = useState({
        username: "",
        email: "",
        password: "",
    });
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    const handleSubmit = async (event) => {
        event.preventDefault();
        setLoading(true);
        setError("");

        try {
            await userApi.post("/users/register", form);
            const authResponse = await authApi.post("/auth/login", {
                username: form.username,
                password: form.password,
            });
            login(authResponse.data);
            navigate("/", { replace: true });
        } catch (requestError) {
            setError(parseApiError(requestError, "Unable to register"));
        } finally {
            setLoading(false);
        }
    };

    return (
        <section className="auth-layout">
            <article className="auth-card auth-card-featured">
                <p className="eyebrow">Create an Account</p>
                <h1>Join the cloud auction system</h1>
                <p className="auth-lead">
                    Register once, then move directly into seller and bidder flows with a real JWT
                    session.
                </p>
                <div className="feature-list">
                    <div className="feature-pill">Unique usernames</div>
                    <div className="feature-pill">Seller and bidder access</div>
                    <div className="feature-pill">Real-time event visibility</div>
                </div>
            </article>

            <section className="auth-card">
                <div className="section-heading">
                    <h2>Create Account</h2>
                    <p>Register a new auction account and start bidding immediately.</p>
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
                            placeholder="choose a username"
                        />
                    </label>

                    <label className="form-field">
                        <span>Email</span>
                        <input
                            className="form-input"
                            type="email"
                            value={form.email}
                            onChange={(event) =>
                                setForm((previous) => ({
                                    ...previous,
                                    email: event.target.value,
                                }))
                            }
                            onFocus={clearAuthNotice}
                            placeholder="you@example.com"
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
                            placeholder="minimum 8 characters"
                        />
                    </label>

                    {authNotice ? <p className="status-warning">{authNotice}</p> : null}
                    {error ? <p className="status-error">{error}</p> : null}

                    <button className="primary-button" disabled={loading} type="submit">
                        {loading ? "Creating account..." : "Register"}
                    </button>
                </form>

                <p className="auth-footer">
                    Already registered? <Link to="/login">Sign in</Link>.
                </p>
            </section>
        </section>
    );
}
