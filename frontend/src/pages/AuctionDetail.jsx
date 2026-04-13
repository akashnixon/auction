import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import API from "../api/api";
import Timer from "../components/Timer";
import BidBox from "../components/BidBox";

export default function AuctionDetail() {
    const { id } = useParams();
    const [auction, setAuction] = useState(null);

    useEffect(() => {
        API.get(`/auctions/${id}`).then(res => setAuction(res.data));

        const ws = new WebSocket(`ws://localhost:8083/ws/auctions/${id}`);

        ws.onmessage = (e) => {
            const data = JSON.parse(e.data);
            setAuction(prev => ({ ...prev, ...data }));
        };

        return () => ws.close();
    }, [id]);

    const placeBid = async (amount) => {
        await API.post("/bids", {
            auctionId: id,
            amount
        });
    };

    if (!auction) return <div>Loading...</div>;

    return (
        <div>
            <h1>{auction.title}</h1>
            <Timer endTime={auction.auctionEndTime} />
            <p>Highest Bid: ${auction.currentPrice}</p>
            <BidBox onBid={placeBid} />
        </div>
    );
}