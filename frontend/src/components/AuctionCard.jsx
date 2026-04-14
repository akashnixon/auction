import { Link } from "react-router-dom";
import defaultAuctionImage from "../assets/default-auction.svg";
import {
    formatCurrency,
    getRelativeTime,
} from "../utils/formatters";

export default function AuctionCard({ auction }) {
    const winner = auction.winnerName || auction.winnerUserId || "Pending";
    const bidLabel = auction.highestBid
        ? formatCurrency(auction.highestBid.amount)
        : "No bids yet";
    const statusLabel = auction.status === "ENDED" ? "Auction ended" : "Auction live";
    const sellerLabel = auction.sellerName || auction.sellerId;

    return (
        <Link to={`/auction/${auction.auctionId}`} className="auction-card-link">
            <article className="auction-card">
                <div className="auction-card-media">
                    <img
                        src={auction.imageDataUrl || defaultAuctionImage}
                        alt={auction.itemName}
                        className="auction-card-image"
                    />
                </div>

                <div className="auction-card-body">
                    <div className="auction-card-headline">
                        <span className="auction-state-tag">{statusLabel}</span>
                        <h3>{auction.itemName}</h3>
                        <p className="auction-meta-line">Seller {sellerLabel}</p>
                        <p className="auction-description">
                            Auction ID {auction.auctionId}.{" "}
                            {auction.status === "ENDED"
                                ? `Winner: ${winner}`
                                : `Closes ${getRelativeTime(auction.endTime)}.`}
                        </p>
                    </div>

                    <div className="auction-card-footer">
                        <div className="auction-price-block">
                            <span>{auction.status === "ENDED" ? "Winning bid" : "Current bid"}</span>
                            <strong>{bidLabel}</strong>
                            <small>{auction.status === "ENDED" ? "Closed" : "Open now"}</small>
                        </div>
                    </div>
                </div>
            </article>
        </Link>
    );
}
