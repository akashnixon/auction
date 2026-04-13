import { useEffect, useState } from "react";
import API from "../api/api";

export default function Auctions() {
    const [data, setData] = useState([]);
    const [error, setError] = useState("");

    useEffect(() => {
        API.get("/auctions")
            .then(res => setData(res.data))
            .catch(err => {
                console.error(err);
                setError("Failed to load auctions (backend may not be running)");
            });
    }, []);

    return (
        <div className="p-4">
            <h1 className="text-xl font-bold mb-4">Auctions</h1>

            {error && (
                <p className="text-red-500">{error}</p>
            )}

            {data.length === 0 && !error && (
                <p>No auctions available</p>
            )}

            {data.map(a => (
                <div key={a.id} className="border p-3 mb-2 rounded">
                    <h2>{a.title}</h2>
                    <p>Price: ${a.currentPrice}</p>
                </div>
            ))}
        </div>
    );
}