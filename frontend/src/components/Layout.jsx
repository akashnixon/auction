import { Link, NavLink, Outlet } from "react-router-dom";
import NotificationPanel from "./NotificationPanel";

function NavItem({ to, children }) {
    return (
        <NavLink
            to={to}
            className={({ isActive }) =>
                `nav-link${isActive ? " nav-link-active" : ""}`
            }
        >
            {children}
        </NavLink>
    );
}

export default function Layout({ user, onLogout, onDeregister, statusMessage }) {
    return (
        <div className="app-shell">
            <header className="topbar">
                <div className="brand-wrap">
                    <Link to="/" className="brand-mark" aria-label="Auction House home">
                        AH
                    </Link>
                    <div className="brand-copy">
                        <Link to="/" className="brand">
                            Auction House
                        </Link>
                        <p className="brand-subtitle">
                            Real-time cloud auction platform
                        </p>
                    </div>
                </div>

                <div className="topbar-actions">
                    <NavItem to="/">Browse auctions</NavItem>
                    {user ? <NavItem to="/create-auction">Create auction</NavItem> : null}
                    {user ? <NavItem to="/my-auctions">My auctions</NavItem> : null}
                    {user ? (
                        <button
                            type="button"
                            className="secondary-button"
                            onClick={onDeregister}
                        >
                            Deregister
                        </button>
                    ) : (
                        <NavItem to="/register">Register</NavItem>
                    )}
                    {user ? (
                        <button
                            type="button"
                            className="primary-button topbar-button"
                            onClick={onLogout}
                        >
                            Sign out
                        </button>
                    ) : (
                        <NavItem to="/login">Sign in</NavItem>
                    )}
                </div>
            </header>

            <section className="status-strip">
                {user ? (
                    <div className="status-strip-row">
                        <span>
                            Signed in as <strong>{user.username}</strong>
                        </span>
                        <span className="muted-text">
                            Your account is ready to create listings and place bids.
                        </span>
                    </div>
                ) : (
                    <span>Browsing as guest. Sign in to create auctions and place bids.</span>
                )}
                {statusMessage ? <p className="banner-status">{statusMessage}</p> : null}
            </section>

            <div className="page-grid">
                <main className="page-content">
                    <Outlet />
                </main>
                <aside className="sidebar">
                    <NotificationPanel />
                </aside>
            </div>
        </div>
    );
}
