import { useCallback, useContext, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import AuctionCard from "../components/AuctionCard";
import { auctionApi, bidApi, parseApiError, userApi } from "../api/api";
import { AuthContext } from "../context/AuthContext";
import { NotificationsContext } from "../context/NotificationsContext";
import { getHighestBid } from "../utils/formatters";

async function enrichAuction(auction, usersById) {
    const bidsResponse = await bidApi.get(`/bids/auction/${auction.auctionId}`);
    const bids = bidsResponse.data.bids || [];
    const highestBid = getHighestBid(bids);

    return {
        ...auction,
        highestBid,
        sellerName: usersById.get(auction.sellerId)?.username || auction.sellerId,
        winnerName: auction.winnerUserId
            ? usersById.get(auction.winnerUserId)?.username || auction.winnerUserId
            : null,
    };
}

export default function MyAuctions() {
    const { user } = useContext(AuthContext);
    const { events } = useContext(NotificationsContext);
    const [auctions, setAuctions] = useState([]);
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(true);
    const [refreshing, setRefreshing] = useState(false);

    const latestEventId = events[0]?.eventId;

    const loadAuctions = useCallback(async () => {
        const initialLoad = auctions.length === 0;
        if (initialLoad) {
            setLoading(true);
        } else {
            setRefreshing(true);
        }

        try {
            const [auctionResponse, usersResponse] = await Promise.all([
                auctionApi.get("/auctions"),
                userApi.get("/users"),
            ]);
            const usersById = new Map(
                (usersResponse.data.users || []).map((item) => [item.id, item])
            );
            const owned = auctionResponse.data.filter(
                (auction) => auction.sellerId === user.userId
            );
            const enriched = await Promise.all(
                owned.map((auction) => enrichAuction(auction, usersById))
            );
            setAuctions(enriched);
            setError("");
        } catch (requestError) {
            setError(parseApiError(requestError, "Unable to load your auctions"));
        } finally {
            setLoading(false);
            setRefreshing(false);
        }
    }, [auctions.length, user.userId]);

    useEffect(() => {
        loadAuctions();
    }, [user.userId, latestEventId, loadAuctions]);

    const grouped = useMemo(
        () => ({
            active: auctions.filter((auction) => auction.status === "ACTIVE"),
            ended: auctions.filter((auction) => auction.status === "ENDED"),
        }),
        [auctions]
    );

    const summary = useMemo(() => {
        const revenue = grouped.ended.reduce((total, auction) => {
            return total + Number(auction.highestBid?.amount || 0);
        }, 0);

        return {
            total: auctions.length,
            active: grouped.active.length,
            ended: grouped.ended.length,
            revenue,
        };
    }, [auctions, grouped]);

    return (
        <section className="stack-lg">
            <section className="panel overview-panel">
                <div className="section-heading split-heading">
                    <div>
                        <p className="eyebrow">Seller Desk</p>
                        <h1>My Auctions</h1>
                        <p>Monitor your live listings, closeouts, and finalized outcomes.</p>
                    </div>
                </div>

                <div className="summary-grid">
                    <article className="summary-card">
                        <span>Total Listings</span>
                        <strong>{summary.total}</strong>
                    </article>
                    <article className="summary-card accent-card">
                        <span>Active Listings</span>
                        <strong>{summary.active}</strong>
                    </article>
                    <article className="summary-card">
                        <span>Finalized Listings</span>
                        <strong>{summary.ended}</strong>
                    </article>
                    <article className="summary-card">
                        <span>Captured Value</span>
                        <strong>${summary.revenue.toFixed(2)}</strong>
                    </article>
                </div>
            </section>

            <section className="panel stack-lg">
                {error ? (
                    <div className="empty-state rich-empty-state">
                        <h3>We couldn&apos;t load your seller view</h3>
                        <p className="muted-text">{error}</p>
                        <button
                            type="button"
                            className="secondary-button"
                            onClick={loadAuctions}
                        >
                            Try again
                        </button>
                    </div>
                ) : null}
                {loading ? <p className="muted-text">Loading your auctions...</p> : null}
                {refreshing && !loading ? (
                    <p className="muted-text subtle-refresh">Refreshing seller view...</p>
                ) : null}

                {!loading && !error ? (
                    <>
                        <div>
                            <div className="subsection-header">
                                <h2>Active</h2>
                                <span className="badge">{grouped.active.length}</span>
                            </div>
                            <div className="auction-grid">
                                {grouped.active.length === 0 ? (
                                    <div className="empty-state compact-empty-state rich-empty-state">
                                        <h3>No active auctions right now</h3>
                                        <p className="muted-text">
                                            Create a new listing to put something live on the board.
                                        </p>
                                        <Link to="/create-auction" className="secondary-button">
                                            Create auction
                                        </Link>
                                    </div>
                                ) : (
                                    grouped.active.map((auction) => (
                                        <AuctionCard key={auction.auctionId} auction={auction} />
                                    ))
                                )}
                            </div>
                        </div>

                        <div>
                            <div className="subsection-header">
                                <h2>Finalized</h2>
                                <span className="badge">{grouped.ended.length}</span>
                            </div>
                            <div className="auction-grid">
                                {grouped.ended.length === 0 ? (
                                    <div className="empty-state compact-empty-state rich-empty-state">
                                        <h3>No finalized auctions yet</h3>
                                        <p className="muted-text">
                                            Closed auctions and winners will appear here.
                                        </p>
                                    </div>
                                ) : (
                                    grouped.ended.map((auction) => (
                                        <AuctionCard key={auction.auctionId} auction={auction} />
                                    ))
                                )}
                            </div>
                        </div>
                    </>
                ) : null}
            </section>
        </section>
    );
}
