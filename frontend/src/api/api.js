import axios from "axios";

const runtimeConfig = window.__APP_CONFIG__ || {};

const config = {
    user:
        runtimeConfig.USER_API_URL ||
        import.meta.env.VITE_USER_API_URL ||
        "http://localhost:3001",
    auth:
        runtimeConfig.AUTH_API_URL ||
        import.meta.env.VITE_AUTH_API_URL ||
        "http://localhost:3002",
    auction:
        runtimeConfig.AUCTION_API_URL ||
        import.meta.env.VITE_AUCTION_API_URL ||
        "http://localhost:3003",
    bid:
        runtimeConfig.BID_API_URL ||
        import.meta.env.VITE_BID_API_URL ||
        "http://localhost:3004",
    notification:
        runtimeConfig.NOTIFICATION_API_URL ||
        import.meta.env.VITE_NOTIFICATION_API_URL ||
        "http://localhost:3005",
};

const createClient = (baseURL) => axios.create({ baseURL });

export const userApi = createClient(config.user);
export const authApi = createClient(config.auth);
export const auctionApi = createClient(config.auction);
export const bidApi = createClient(config.bid);
export const notificationApi = createClient(config.notification);

const protectedClients = [auctionApi, bidApi];
let sessionExpiryHandler = null;
let sessionExpiryNotified = false;

export const registerSessionExpiryHandler = (handler) => {
    sessionExpiryHandler = handler;
};

const notifySessionExpired = () => {
    if (sessionExpiryNotified) {
        return;
    }
    sessionExpiryNotified = true;
    sessionExpiryHandler?.(
        "Your session expired. Sign in again to keep bidding or creating listings."
    );
};

protectedClients.forEach((client) => {
    client.interceptors.response.use(
        (response) => response,
        (error) => {
            if (error?.response?.status === 401) {
                notifySessionExpired();
            }
            return Promise.reject(error);
        }
    );
});

export const setAuthToken = (token) => {
    protectedClients.forEach((client) => {
        if (token) {
            client.defaults.headers.common.Authorization = `Bearer ${token}`;
            sessionExpiryNotified = false;
        } else {
            delete client.defaults.headers.common.Authorization;
            sessionExpiryNotified = false;
        }
    });
};

export const getNotificationStreamUrl = () => `${config.notification}/stream`;

export const createIdempotencyKey = () =>
    `bid-${Date.now()}-${Math.random().toString(16).slice(2)}`;

export const parseApiError = (error, fallbackMessage) =>
    error?.response?.data?.error ||
    error?.message ||
    fallbackMessage ||
    "Request failed";
