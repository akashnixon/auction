import { Link } from "react-router-dom";

export default function AuctionCard({ auction }) {
    return (
        <div className="border p-4 rounded shadow">
            <h2>{auction.title}</h2>
            <p>Bid: ${auction.currentPrice}</p>
            <Link to={`/auction/${auction.id}`}>
                <button className="bg-blue-500 text-white px-2 py-1">
                    View
                </button>
            </Link>
        </div>
    );
}