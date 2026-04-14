import { useState } from "react";

export default function BidBox({ onBid, disabled, submitting }) {
    const [amount, setAmount] = useState("");

    const submit = () => {
        if (!amount) {
            return;
        }
        onBid(amount);
        setAmount("");
    };

    return (
        <div className="bid-box-wrap">
            <div className="bid-box">
                <input
                    type="number"
                    min="0"
                    step="0.01"
                    value={amount}
                    onChange={(event) => setAmount(event.target.value)}
                    className="form-input"
                    placeholder="Enter your bid"
                    disabled={disabled}
                />
                <button
                    type="button"
                    onClick={submit}
                    className="primary-button"
                    disabled={disabled || submitting}
                >
                    {submitting ? "Submitting..." : "Place Bid"}
                </button>
            </div>
            <p className="muted-text bid-footnote">
                Bids are validated server-side. Highest amount wins, and equal bids
                are resolved by earliest server arrival.
            </p>
        </div>
    );
}
