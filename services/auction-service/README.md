# Auction Service

## Purpose
The Auction Service manages the **auction lifecycle** from item advertisement to auction completion.

Each auction lasts exactly **5 minutes**, as specified in the project requirements.

---

## Responsibilities
- Advertise items for auction
- Start and manage 5-minute auction timers
- Restart auctions if no valid bids are received
- Finalize auctions and determine winners
- Notify sellers and winners

---

## APIs
- `POST /auctions`
- `GET /auctions/active`
- `GET /auctions/{auctionId}`

---

## Core Logic
- Auction timers are managed internally
- Leader election ensures only one instance finalizes an auction
- Auction results are persisted reliably

---

## Data Managed
- Auction metadata
- Auction state (active, ended, restarted)
- Winning bid reference

---

## Notes
This service is designed to tolerate failures during auction execution and guarantees correct auction finalization.
