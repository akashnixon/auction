import { useEffect, useMemo, useState } from "react";

function formatRemaining(milliseconds) {
    const totalSeconds = Math.max(0, Math.floor(milliseconds / 1000));
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

export default function Timer({ endTime, status }) {
    const calculate = useMemo(() => {
        if (!endTime || status === "ENDED") {
            return 0;
        }
        return new Date(endTime).getTime() - Date.now();
    }, [endTime, status]);

    const [timeLeft, setTimeLeft] = useState(calculate);

    useEffect(() => {
        setTimeLeft(calculate);
        const interval = setInterval(() => {
            setTimeLeft(
                status === "ENDED" ? 0 : new Date(endTime).getTime() - Date.now()
            );
        }, 1000);
        return () => clearInterval(interval);
    }, [calculate, endTime, status]);

    return (
        <span className={`timer ${timeLeft <= 30000 ? "timer-warning" : ""}`}>
            {status === "ENDED" ? "Closed" : formatRemaining(timeLeft)}
        </span>
    );
}
