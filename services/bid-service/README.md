# Bid Service

## Purpose
The Bid Service handles **bid submission, validation, and winner resolution**.

It ensures correctness under high concurrency and enforces bidding rules.

---

## Responsibilities
- Accept bids for active auctions
- Validate bid amounts
- Resolve race conditions
- Enforce highest-bid-wins rule
- Resolve ties using server-side timestamps

---

## APIs
- `POST /bids`
- `GET /bids/auction/{auctionId}`

---

## Performance Design
- Redis is used to store:
  - Current highest bid
  - Bid timestamps
- Persistent storage is used for auditability

---

## Data Managed
- Bid history
- Current highest bids (cached)

---

## Notes
The Bid Service is optimized for burst traffic and scales independently to handle concurrent bidding.
