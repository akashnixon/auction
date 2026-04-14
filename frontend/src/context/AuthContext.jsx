import {
    createContext,
    useCallback,
    useEffect,
    useMemo,
    useState,
} from "react";
import {
    registerSessionExpiryHandler,
    setAuthToken,
} from "../api/api";

export const AuthContext = createContext(null);

const TOKEN_KEY = "auction_token";
const USER_KEY = "auction_user";
const NOTICE_KEY = "auction_notice";

export function AuthProvider({ children }) {
    const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY));
    const [user, setUser] = useState(() => {
        const raw = localStorage.getItem(USER_KEY);
        return raw ? JSON.parse(raw) : null;
    });
    const [authNotice, setAuthNotice] = useState(
        () => localStorage.getItem(NOTICE_KEY) || ""
    );

    useEffect(() => {
        setAuthToken(token);
    }, [token]);

    const clearAuthNotice = useCallback(() => {
        localStorage.removeItem(NOTICE_KEY);
        setAuthNotice("");
    }, []);

    const setNotice = useCallback((message) => {
        if (message) {
            localStorage.setItem(NOTICE_KEY, message);
            setAuthNotice(message);
            return;
        }
        localStorage.removeItem(NOTICE_KEY);
        setAuthNotice("");
    }, []);

    const login = useCallback((payload) => {
        const nextUser = {
            userId: payload.userId,
            username: payload.username,
        };
        localStorage.setItem(TOKEN_KEY, payload.token);
        localStorage.setItem(USER_KEY, JSON.stringify(nextUser));
        setToken(payload.token);
        setUser(nextUser);
        setNotice("");
    }, [setNotice]);

    const logout = useCallback((options = {}) => {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
        setToken(null);
        setUser(null);
        setAuthToken(null);
        setNotice(options.notice || "");
    }, [setNotice]);

    useEffect(() => {
        registerSessionExpiryHandler((message) => {
            if (token || user) {
                logout({ notice: message });
            }
        });
    }, [logout, token, user]);

    const value = useMemo(
        () => ({
            token,
            user,
            authNotice,
            isAuthenticated: Boolean(token && user),
            login,
            logout,
            clearAuthNotice,
        }),
        [token, user, authNotice, login, logout, clearAuthNotice]
    );

    return (
        <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
    );
}
