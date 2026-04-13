import { useState } from "react";

export default function BidBox({ onBid }) {
    const [amount, setAmount] = useState("");

    return (
        <div>
            <input
                type="number"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                className="border p-2"
            />
            <button
                onClick={() => onBid(amount)}
                className="bg-green-500 text-white px-3 py-1 ml-2"
            >
                Bid
            </button>
        </div>
    );
}