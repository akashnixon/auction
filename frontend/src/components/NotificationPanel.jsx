import { useContext, useEffect, useMemo, useState } from "react";
import { NotificationsContext } from "../context/NotificationsContext";
import { auctionApi, userApi } from "../api/api";
import { formatCurrency, formatRelativeTime } from "../utils/formatters";

function getVariant(type) {
    switch (type) {
        case "AUCTION_WON":
            return "good";
        case "AUCTION_SOLD":
        case "AUCTION_FINALIZED":
            return "warm";
        case "BID_PLACED":
        case "BID_PLACED_LEADING":
        case "HIGHEST_BID_CHANGED":
            return "cool";
        case "AUCTION_RESTARTED":
            return "neutral";
        default:
            return "neutral";
    }
}

function resolveUserName(userLookup, payload, idKey, nameKey) {
    if (payload?.[nameKey]) {
        return payload[nameKey];
    }
    if (payload?.[idKey]) {
        return userLookup[payload[idKey]] || payload[idKey];
    }
    return null;
}

function getNotificationTitle(event) {
    switch (event.type) {
        case "AUCTION_ADVERTISED":
            return "Auction Started";
        case "BID_PLACED":
            return "New Bid";
        case "BID_PLACED_LEADING":
            return "New Leading Bid";
        case "HIGHEST_BID_CHANGED":
            return "Leading Bid Updated";
        case "AUCTION_FINALIZED":
            return "Auction Ended";
        case "AUCTION_RESTARTED":
            return "Auction Restarted";
        case "AUCTION_CYCLE_CLOSED":
            return "Bidding Window Closed";
        case "AUCTION_WON":
            return "Auction Won";
        case "AUCTION_SOLD":
            return "Item Sold";
        default:
            return event.type.replaceAll("_", " ");
    }
}

function renderMessage(event, userLookup, auctionLookup) {
    const payload = event.payload || {};
    const winnerName = resolveUserName(
        userLookup,
        payload,
        "winnerUserId",
        "winnerName"
    );
    const bidderName = resolveUserName(
        userLookup,
        payload,
        "bidderId",
        "bidderName"
    );
    const itemName =
        payload.itemName ||
        (payload.auctionId ? auctionLookup[payload.auctionId] : null) ||
        `Auction ${payload.auctionId}`;

    switch (event.type) {
        case "AUCTION_ADVERTISED":
            return `${itemName} just opened for live bidding.`;
        case "BID_PLACED":
            return `${bidderName || "A bidder"} placed ${formatCurrency(payload.amount)} on ${itemName}.`;
        case "BID_PLACED_LEADING":
            return `${bidderName || "A bidder"} placed a new leading bid of ${formatCurrency(payload.amount)} on ${itemName}.`;
        case "HIGHEST_BID_CHANGED":
            return `${bidderName || "A bidder"} is now leading ${itemName} with ${formatCurrency(payload.amount)}.`;
        case "AUCTION_FINALIZED":
            return `${itemName} ended${winnerName ? ` with ${winnerName} winning` : ""}${payload.amount ? ` at ${formatCurrency(payload.amount)}` : ""}.`;
        case "AUCTION_RESTARTED":
            return `${itemName} reopened because the previous window ended without a valid bid.`;
        case "AUCTION_CYCLE_CLOSED":
            return `${itemName} closed its current bidding window and is being processed.`;
        case "AUCTION_WON":
            return `You won ${itemName}${payload.amount ? ` for ${formatCurrency(payload.amount)}` : ""}.`;
        case "AUCTION_SOLD":
            return `${itemName} has been sold${winnerName ? ` to ${winnerName}` : ""}${payload.amount ? ` for ${formatCurrency(payload.amount)}` : ""}.`;
        default:
            return `${event.type} received.`;
    }
}

export default function NotificationPanel() {
    const { events, error, connectionState } = useContext(NotificationsContext);
    const [auctionLookup, setAuctionLookup] = useState({});
    const [userLookup, setUserLookup] = useState({});
    const displayEvents = useMemo(() => {
        const merged = [];

        events.forEach((event) => {
            const previous = merged[merged.length - 1];
            const sameBid =
                previous?.payload?.auctionId === event.payload?.auctionId &&
                previous?.payload?.bidId &&
                previous.payload.bidId === event.payload?.bidId;

            if (
                event.type === "BID_PLACED" &&
                previous?.type === "HIGHEST_BID_CHANGED" &&
                sameBid
            ) {
                merged[merged.length - 1] = {
                    ...previous,
                    type: "BID_PLACED_LEADING",
                    payload: {
                        ...event.payload,
                        ...previous.payload,
                    },
                };
                return;
            }

            if (
                event.type === "AUCTION_CYCLE_CLOSED" &&
                previous?.type === "AUCTION_FINALIZED" &&
                previous?.payload?.auctionId === event.payload?.auctionId
            ) {
                return;
            }

            merged.push(event);
        });

        return merged;
    }, [events]);
    const latestEvent = displayEvents[0];

    useEffect(() => {
        let cancelled = false;

        Promise.all([userApi.get("/users"), auctionApi.get("/auctions")])
            .then(([usersResponse, auctionsResponse]) => {
                if (cancelled) {
                    return;
                }
                setUserLookup(
                    Object.fromEntries(
                        (usersResponse.data.users || []).map((item) => [
                            item.id,
                            item.username || item.id,
                        ])
                    )
                );
                setAuctionLookup(
                    Object.fromEntries(
                        (auctionsResponse.data || []).map((item) => [
                            item.auctionId,
                            item.itemName || item.auctionId,
                        ])
                    )
                );
            })
            .catch(() => {
                if (!cancelled) {
                    setUserLookup({});
                    setAuctionLookup({});
                }
            });

        return () => {
            cancelled = true;
        };
    }, [displayEvents]);

    return (
        <section className="panel notification-panel">
            <div className="panel-header">
                <div>
                    <p className="eyebrow">Live Activity</p>
                    <h2>Notification Feed</h2>
                </div>
                <span className={`badge badge-${connectionState.toLowerCase()}`}>
                    {connectionState}
                </span>
            </div>

            {latestEvent ? (
                <div className="notification-spotlight">
                    <p className="eyebrow">Latest Event</p>
                    <strong>{renderMessage(latestEvent, userLookup, auctionLookup)}</strong>
                    <span className="muted-text">
                        {formatRelativeTime(latestEvent.createdAt)}
                    </span>
                </div>
            ) : null}

            {error ? <p className="status-error">{error}</p> : null}

            <div className="notification-list">
                {displayEvents.length === 0 ? (
                    <p className="muted-text">No live events yet.</p>
                ) : (
                    displayEvents.map((event) => (
                        <article
                            key={event.eventId}
                            className={`notification-item notification-${getVariant(event.type)}`}
                        >
                            <div className="notification-topline">
                                <strong>{getNotificationTitle(event)}</strong>
                                <span>{formatRelativeTime(event.createdAt)}</span>
                            </div>
                            <p>{renderMessage(event, userLookup, auctionLookup)}</p>
                        </article>
                    ))
                )}
            </div>
        </section>
    );
}
