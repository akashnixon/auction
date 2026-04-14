import { useContext, useState } from "react";
import {
    BrowserRouter,
    Navigate,
    Route,
    Routes,
    useNavigate,
} from "react-router-dom";
import { parseApiError, userApi } from "./api/api";
import Layout from "./components/Layout";
import RequireAuth from "./components/RequireAuth";
import { AuthContext } from "./context/AuthContext";
import AuctionDetail from "./pages/AuctionDetail";
import Auctions from "./pages/Auctions";
import CreateAuction from "./pages/CreateAuction";
import Login from "./pages/Login";
import MyAuctions from "./pages/MyAuctions";
import Register from "./pages/Register";

function AppRoutes() {
    const { user, logout, authNotice, clearAuthNotice } = useContext(AuthContext);
    const navigate = useNavigate();
    const [statusMessage, setStatusMessage] = useState("");

    const handleLogout = () => {
        logout();
        setStatusMessage("");
        clearAuthNotice();
        navigate("/login");
    };

    const handleDeregister = async () => {
        if (!user) {
            return;
        }

        try {
            await userApi.post("/users/deregister", {
                userId: user.userId,
            });
            logout();
            navigate("/login");
        } catch (error) {
            setStatusMessage(parseApiError(error, "Unable to deregister"));
        }
    };

    return (
        <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            <Route
                path="/"
                element={
                    <Layout
                        user={user}
                        onLogout={handleLogout}
                        onDeregister={handleDeregister}
                        statusMessage={statusMessage || authNotice}
                    />
                }
            >
                <Route index element={<Auctions />} />
                <Route path="auction/:id" element={<AuctionDetail />} />
                <Route
                    path="create-auction"
                    element={
                        <RequireAuth>
                            <CreateAuction />
                        </RequireAuth>
                    }
                />
                <Route
                    path="my-auctions"
                    element={
                        <RequireAuth>
                            <MyAuctions />
                        </RequireAuth>
                    }
                />
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    );
}

export default function App() {
    return (
        <BrowserRouter>
            <AppRoutes />
        </BrowserRouter>
    );
}
