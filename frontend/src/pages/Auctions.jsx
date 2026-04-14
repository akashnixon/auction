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

export default function Auctions() {
    const { user } = useContext(AuthContext);
    const { events } = useContext(NotificationsContext);
    const [auctions, setAuctions] = useState([]);
    const [loading, setLoading] = useState(true);
    const [refreshing, setRefreshing] = useState(false);
    const [error, setError] = useState("");
    const [query, setQuery] = useState("");
    const [statusFilter, setStatusFilter] = useState("ALL");

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
            const enriched = await Promise.all(
                auctionResponse.data.map((auction) => enrichAuction(auction, usersById))
            );
            setAuctions(enriched);
            setError("");
        } catch (requestError) {
            setError(parseApiError(requestError, "Unable to load auctions"));
        } finally {
            setLoading(false);
            setRefreshing(false);
        }
    }, [auctions.length]);

    useEffect(() => {
        loadAuctions();
    }, [latestEventId, loadAuctions]);

    const stats = useMemo(() => {
        const active = auctions.filter((auction) => auction.status === "ACTIVE").length;
        const ended = auctions.filter((auction) => auction.status === "ENDED").length;
        const competitive = auctions.filter((auction) => auction.highestBid).length;

        return { total: auctions.length, active, ended, competitive };
    }, [auctions]);

    const filteredAuctions = useMemo(() => {
        return auctions
            .filter((auction) => {
                const matchesQuery = [auction.itemName, auction.auctionId, auction.sellerId]
                    .concat(auction.sellerName ? [auction.sellerName] : [])
                    .filter(Boolean)
                    .some((value) => value.toLowerCase().includes(query.toLowerCase()));
                const matchesStatus =
                    statusFilter === "ALL" || auction.status === statusFilter;
                return matchesQuery && matchesStatus;
            })
            .sort((left, right) => new Date(right.endTime) - new Date(left.endTime));
    }, [auctions, query, statusFilter]);

    return (
        <section className="stack-lg">
            <section className="section-intro">
                <div className="section-heading split-heading">
                    <div>
                        <h1>
                            {statusFilter === "ENDED"
                                ? "Completed auctions"
                                : statusFilter === "ACTIVE"
                                  ? "Live auctions"
                                  : "Auction house"}
                        </h1>
                        <p>
                            Browse active and completed listings, track winner outcomes,
                            and jump directly into any auction room.
                        </p>
                    </div>
                    <div className="section-note">
                        {user
                            ? "Track live status and winner outcomes without leaving the board."
                            : "Sign in to bid, create listings, and follow account-specific activity."}
                    </div>
                </div>

                <div className="summary-strip">
                    <article className="summary-chip">
                        <span>Total Auctions</span>
                        <strong>{stats.total}</strong>
                    </article>
                    <article className="summary-chip">
                        <span>Active Cycles</span>
                        <strong>{stats.active}</strong>
                    </article>
                    <article className="summary-chip">
                        <span>Competitive Rooms</span>
                        <strong>{stats.competitive}</strong>
                    </article>
                    <article className="summary-chip">
                        <span>Finalized</span>
                        <strong>{stats.ended}</strong>
                    </article>
                </div>
            </section>

            <section className="panel">
                <div className="filter-bar">
                    <label className="filter-field">
                        <span>Search</span>
                        <input
                            className="form-input"
                            value={query}
                            onChange={(event) => setQuery(event.target.value)}
                            placeholder="Search by item, seller, or auction ID"
                        />
                    </label>

                    <label className="filter-field filter-select-wrap">
                        <span>Status</span>
                        <select
                            className="form-input"
                            value={statusFilter}
                            onChange={(event) => setStatusFilter(event.target.value)}
                        >
                            <option value="ALL">All</option>
                            <option value="ACTIVE">Active</option>
                            <option value="ENDED">Ended</option>
                        </select>
                    </label>
                </div>

                {error ? (
                    <div className="empty-state rich-empty-state">
                        <h3>We couldn&apos;t load the auction board</h3>
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
                {loading ? <p className="muted-text">Loading auctions...</p> : null}
                {refreshing && !loading ? (
                    <p className="muted-text subtle-refresh">Refreshing live data...</p>
                ) : null}

                {!loading && !error ? (
                    <div className="auction-grid">
                        {filteredAuctions.length === 0 ? (
                            <div className="empty-state rich-empty-state">
                                <h3>No auctions match this view</h3>
                                <p className="muted-text">
                                    Adjust your search or status filter to open up more rooms.
                                </p>
                                {user ? (
                                    <Link to="/create-auction" className="secondary-button">
                                        Create a new auction
                                    </Link>
                                ) : null}
                            </div>
                        ) : (
                            filteredAuctions.map((auction) => (
                                <AuctionCard key={auction.auctionId} auction={auction} />
                            ))
                        )}
                    </div>
                ) : null}
            </section>
        </section>
    );
}
