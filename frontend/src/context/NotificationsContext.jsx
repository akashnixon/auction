import {
    createContext,
    useCallback,
    useEffect,
    useMemo,
    useRef,
    useState,
} from "react";
import {
    getNotificationStreamUrl,
    notificationApi,
    parseApiError,
} from "../api/api";

export const NotificationsContext = createContext(null);

const EVENT_TYPES = [
    "AUCTION_ADVERTISED",
    "BID_PLACED",
    "HIGHEST_BID_CHANGED",
    "AUCTION_FINALIZED",
    "AUCTION_RESTARTED",
    "AUCTION_WON",
    "AUCTION_SOLD",
    "AUCTION_CYCLE_CLOSED",
    "SMOKE_TEST_EVENT",
];

export function NotificationsProvider({ children }) {
    const [events, setEvents] = useState([]);
    const [error, setError] = useState("");
    const [connectionState, setConnectionState] = useState("Connecting");
    const sourceRef = useRef(null);

    const pushEvent = useCallback((event) => {
        setEvents((previous) => {
            const next = [event, ...previous.filter((item) => item.eventId !== event.eventId)];
            return next.slice(0, 30);
        });
    }, []);

    useEffect(() => {
        let mounted = true;

        notificationApi
            .get("/events?limit=20")
            .then((response) => {
                if (mounted) {
                    setEvents(response.data.events || []);
                }
            })
            .catch((requestError) => {
                if (mounted) {
                    setError(
                        parseApiError(
                            requestError,
                            "Unable to load notification history"
                        )
                    );
                }
            });

        const eventSource = new EventSource(getNotificationStreamUrl());
        sourceRef.current = eventSource;

        eventSource.onopen = () => {
            setConnectionState("Live");
            setError("");
        };

        EVENT_TYPES.forEach((type) => {
            eventSource.addEventListener(type, (message) => {
                try {
                    pushEvent(JSON.parse(message.data));
                    setConnectionState("Live");
                    setError("");
                } catch (parseError) {
                    console.error("Failed to parse SSE event", parseError);
                }
            });
        });

        eventSource.onerror = () => {
            setConnectionState("Retrying");
            setError("Live updates are temporarily unavailable");
        };

        return () => {
            mounted = false;
            eventSource.close();
        };
    }, [pushEvent]);

    const value = useMemo(
        () => ({
            events,
            error,
            connectionState,
        }),
        [events, error, connectionState]
    );

    return (
        <NotificationsContext.Provider value={value}>
            {children}
        </NotificationsContext.Provider>
    );
}
