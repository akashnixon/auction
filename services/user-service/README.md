# User Service

## Purpose
The User Service manages **user registration, deregistration, and user state validation** for the Auction System.

It enforces system rules related to user participation in auctions.

---

## Responsibilities
- Register new users with a unique name
- Deregister users when allowed
- Prevent deregistration if:
  - The user is currently selling an item
  - The user is the highest bidder in at least one active auction

---

## APIs
- `POST /users/register`
- `POST /users/deregister`
- `GET /users/{userId}`

---

## Data Managed
- User identity
- User status (active, seller, highest bidder)

---

## Security
- JWT-based authentication
- Authorization enforced for user operations

---

## Notes
This service is stateless and horizontally scalable. User state constraints are validated via communication with Auction and Bid services.
