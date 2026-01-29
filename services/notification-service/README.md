# Notification Service

## Purpose
The Notification Service provides **real-time updates** to all registered users.

It ensures that changes in auction state and bid updates are immediately visible.

---

## Responsibilities
- Broadcast bid updates in real time
- Notify users when auctions end
- Notify sellers and winning bidders

---

## Technologies
- WebSockets or Server-Sent Events (SSE)
- Message broker subscription for events

---

## Events Handled
- New highest bid
- Auction end
- Auction restart

---

## Notes
This service is optimized for fan-out and can scale independently to support many connected clients.
