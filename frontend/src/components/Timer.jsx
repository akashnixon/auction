import { useEffect, useState } from "react";

export default function Timer({ endTime }) {
    const [timeLeft, setTimeLeft] = useState(0);

    useEffect(() => {
        const interval = setInterval(() => {
            const diff = new Date(endTime) - new Date();
            setTimeLeft(diff > 0 ? diff : 0);
        }, 1000);
        return () => clearInterval(interval);
    }, [endTime]);

    const sec = Math.floor(timeLeft / 1000);
    const min = Math.floor(sec / 60);

    return <div className="text-red-500">{min}:{sec % 60}</div>;
}