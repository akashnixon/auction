import { useCallback, useContext, useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import BidBox from "../components/BidBox";
import Timer from "../components/Timer";
import {
    auctionApi,
    bidApi,
    createIdempotencyKey,
    parseApiError,
    userApi,
} from "../api/api";
import { AuthContext } from "../context/AuthContext";
import { NotificationsContext } from "../context/NotificationsContext";
import {
    formatCurrency,
    formatDateTime,
    getHighestBid,
    getInitials,
} from "../utils/formatters";
import defaultAuctionImage from "../assets/default-auction.svg";

export default function AuctionDetail() {
    const { id } = useParams();
    const { user, isAuthenticated } = useContext(AuthContext);
    const { events } = useContext(NotificationsContext);
    const [auction, setAuction] = useState(null);
    const [bids, setBids] = useState([]);
    const [userLookup, setUserLookup] = useState({});
    const [loading, setLoading] = useState(true);
    const [refreshing, setRefreshing] = useState(false);
    const [error, setError] = useState("");
    const [bidMessage, setBidMessage] = useState("");
    const [placingBid, setPlacingBid] = useState(false);
    const hasLoadedAuction = Boolean(auction);

    const latestRelevantEventId = useMemo(() => {
        const match = events.find((event) => event.payload?.auctionId === id);
        return match?.eventId;
    }, [events, id]);

    const loadAuction = useCallback(async () => {
        const initialLoad = !auction;
        if (initialLoad) {
            setLoading(true);
        } else {
            setRefreshing(true);
        }

        try {
            const [auctionResponse, bidsResponse, usersResponse] = await Promise.all([
                auctionApi.get(`/auctions/${id}`),
                bidApi.get(`/bids/auction/${id}`),
                userApi.get("/users"),
            ]);
            const nextLookup = Object.fromEntries(
                (usersResponse.data.users || []).map((item) => [
                    item.id,
                    item.username || item.id,
                ])
            );

            setAuction(auctionResponse.data);
            setBids(bidsResponse.data.bids || []);
            setUserLookup(nextLookup);
            setError("");
        } catch (requestError) {
            setError(parseApiError(requestError, "Unable to load auction"));
        } finally {
            setLoading(false);
            setRefreshing(false);
        }
    }, [hasLoadedAuction, id]);

    useEffect(() => {
        loadAuction();
    }, [latestRelevantEventId, loadAuction]);

    const highestBid = getHighestBid(bids);
    const bidHistory = useMemo(
        () =>
            [...bids].sort(
                (left, right) =>
                    new Date(right.receivedAt).getTime() -
                    new Date(left.receivedAt).getTime()
            ),
        [bids]
    );
    const metrics = useMemo(
        () => ({
            bids: bids.length,
            highestBid: highestBid ? formatCurrency(highestBid.amount) : "No bids yet",
            closingTime: auction ? formatDateTime(auction.endTime) : "Unknown",
        }),
        [auction, bids.length, highestBid]
    );
    const sellerName = auction ? userLookup[auction.sellerId] || auction.sellerId : "";
    const winnerName = auction?.winnerUserId
        ? userLookup[auction.winnerUserId] || auction.winnerUserId
        : "Pending";
    const leadingBidderName = highestBid?.bidderId
        ? userLookup[highestBid.bidderId] || highestBid.bidderId
        : "No bids have been placed yet.";

    const placeBid = async (amount) => {
        if (!user) {
            setBidMessage("Sign in before placing a bid.");
            return;
        }

        const optimisticBid = {
            bidId: `optimistic-${Date.now()}`,
            auctionId: id,
            bidderId: user.userId,
            amount: Number(amount),
            receivedAt: new Date().toISOString(),
            optimistic: true,
        };

        try {
            setBidMessage("");
            setPlacingBid(true);
            setBids((previous) => [...previous, optimisticBid]);
            const response = await bidApi.post("/bids", {
                auctionId: id,
                bidderId: user.userId,
                amount: Number(amount),
                idempotencyKey: createIdempotencyKey(),
            });
            setBids((previous) =>
                previous.map((bid) =>
                    bid.bidId === optimisticBid.bidId ? response.data : bid
                )
            );
            setBidMessage("Bid submitted successfully.");
        } catch (requestError) {
            setBids((previous) =>
                previous.filter((bid) => bid.bidId !== optimisticBid.bidId)
            );
            setBidMessage(parseApiError(requestError, "Unable to place bid"));
        } finally {
            setPlacingBid(false);
        }
    };

    if (loading) {
        return (
            <section className="panel empty-state rich-empty-state">
                <h2>Loading auction room</h2>
                <p className="muted-text">
                    We&apos;re syncing the latest listing details and bid history.
                </p>
            </section>
        );
    }

    if (error) {
        return (
            <section className="panel empty-state rich-empty-state">
                <h2>We couldn&apos;t load this auction</h2>
                <p className="muted-text">{error}</p>
                <button type="button" className="secondary-button" onClick={loadAuction}>
                    Try again
                </button>
            </section>
        );
    }

    if (!auction) {
        return (
            <section className="panel empty-state rich-empty-state">
                <h2>Auction not found</h2>
                <p className="muted-text">
                    This room may have been removed or the link is no longer valid.
                </p>
                <Link className="secondary-button" to="/">
                    Back to auctions
                </Link>
            </section>
        );
    }

    return (
        <section className="stack-lg">
            <div className="detail-back-link">
                <Link to="/">← back to auction house</Link>
            </div>

            <section className="detail-hero-card">
                <div className="detail-media">
                    {auction.imageDataUrl ? (
                        <img
                            src={auction.imageDataUrl}
                            alt={auction.itemName}
                            className="detail-media-image"
                        />
                    ) : (
                        <img
                            src={defaultAuctionImage}
                            alt={auction.itemName}
                            className="detail-media-image"
                        />
                    )}
                    <div className="detail-media-mark detail-media-mark-overlay">
                        {getInitials(auction.itemName)}
                    </div>
                </div>

                <div className="detail-summary">
                    <span className="auction-state-tag">
                        {auction.status === "ENDED" ? "Auction ended" : "Auction live"}
                    </span>
                    <h1>{auction.itemName}</h1>
                    <p className="detail-subtitle">Cloud Auction Platform</p>
                    <p className="detail-description">
                        Seller {sellerName}. This listing follows server-authoritative
                        timing and winner selection.
                    </p>

                    <div className="detail-summary-grid">
                        <div>
                            <span>Current bid</span>
                            <strong>{metrics.highestBid}</strong>
                        </div>
                        <div>
                            <span>Total bids</span>
                            <strong>{metrics.bids}</strong>
                        </div>
                        <div>
                            <span>Time left</span>
                            <strong>
                                <Timer endTime={auction.endTime} status={auction.status} />
                            </strong>
                        </div>
                        <div>
                            <span>Winner</span>
                            <strong>{winnerName}</strong>
                        </div>
                    </div>
                </div>

                <aside className="detail-bid-panel">
                    <div className="bid-panel-card">
                        <h2>{auction.status === "ENDED" ? "Final result" : "Place a bid"}</h2>
                        <div className="bid-panel-price">
                            <span>{auction.status === "ENDED" ? "Winning bid" : "Current bid"}</span>
                            <strong>{metrics.highestBid}</strong>
                        </div>
                        <p className="muted-text">
                            {highestBid
                                ? `Leading bidder: ${leadingBidderName}`
                                : "No bids have been placed yet."}
                        </p>

                        {!isAuthenticated ? (
                            <p className="muted-text">Sign in to bid on this auction.</p>
                        ) : (
                            <BidBox
                                onBid={placeBid}
                                disabled={auction.status !== "ACTIVE"}
                                submitting={placingBid}
                            />
                        )}

                        {bidMessage ? (
                            <p
                                className={
                                    bidMessage.includes("success")
                                        ? "status-success"
                                        : "status-error"
                                }
                            >
                                {bidMessage}
                            </p>
                        ) : null}
                    </div>
                </aside>
            </section>

            <section className="panel detail-info-panel">
                {refreshing ? (
                    <p className="muted-text subtle-refresh">Refreshing auction activity...</p>
                ) : null}
                <div className="detail-info-grid">
                    <div>
                        <span>Seller</span>
                        <strong>{sellerName}</strong>
                    </div>
                    <div>
                        <span>Auction ID</span>
                        <strong>{auction.auctionId}</strong>
                    </div>
                    <div>
                        <span>Close time</span>
                        <strong>{metrics.closingTime}</strong>
                    </div>
                </div>
            </section>

            <section className="panel bid-history-panel">
                <div className="panel-header">
                    <h2>Bid history</h2>
                </div>
                {bidHistory.length === 0 ? (
                    <p className="muted-text">No bids have been placed yet.</p>
                ) : (
                    <div className="bid-table-wrap">
                        <div className="bid-table-head">
                            <span>Bidder</span>
                            <span>Amount</span>
                            <span>Time</span>
                        </div>
                        <div className="bid-table-body">
                            {bidHistory.map((bid, index) => (
                                <div
                                    key={bid.bidId}
                                    className={`bid-table-row${index === 0 ? " bid-table-row-leading" : ""}`}
                                >
                                    <span>
                                        {userLookup[bid.bidderId] || bid.bidderId}
                                        {bid.optimistic ? " (sending...)" : ""}
                                    </span>
                                    <strong>{formatCurrency(bid.amount)}</strong>
                                    <span>{formatDateTime(bid.receivedAt)}</span>
                                </div>
                            ))}
                        </div>
                    </div>
                )}
            </section>
        </section>
    );
}
