export function formatCurrency(value) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
        return "No bids yet";
    }

    return new Intl.NumberFormat("en-CA", {
        style: "currency",
        currency: "CAD",
        maximumFractionDigits: 2,
    }).format(numeric);
}

export function formatDateTime(value) {
    if (!value) {
        return "Unknown";
    }

    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return "Unknown";
    }

    return date.toLocaleString();
}

export function formatRelativeTime(value) {
    if (!value) {
        return "just now";
    }

    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return "just now";
    }

    const diffMs = date.getTime() - Date.now();
    const minutes = Math.round(diffMs / 60000);

    if (Math.abs(minutes) < 1) {
        return "just now";
    }

    const formatter = new Intl.RelativeTimeFormat("en", { numeric: "auto" });
    if (Math.abs(minutes) < 60) {
        return formatter.format(minutes, "minute");
    }

    const hours = Math.round(minutes / 60);
    if (Math.abs(hours) < 24) {
        return formatter.format(hours, "hour");
    }

    const days = Math.round(hours / 24);
    return formatter.format(days, "day");
}

export function getRelativeTime(value) {
    return formatRelativeTime(value);
}

export function getHighestBid(bids) {
    if (!bids || bids.length === 0) {
        return null;
    }

    return bids.reduce((highest, bid) => {
        if (Number(bid.amount) > Number(highest.amount)) {
            return bid;
        }

        if (
            Number(bid.amount) === Number(highest.amount) &&
            new Date(bid.receivedAt).getTime() <
                new Date(highest.receivedAt).getTime()
        ) {
            return bid;
        }

        return highest;
    });
}

export function getInitials(value) {
    if (!value) {
        return "AS";
    }

    const parts = String(value)
        .split(/[\s-_]+/)
        .filter(Boolean)
        .slice(0, 2);

    if (parts.length === 0) {
        return "AS";
    }

    return parts.map((part) => part[0].toUpperCase()).join("");
}
