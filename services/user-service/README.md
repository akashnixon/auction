# User Service - Identity Layer

## Overview

The User Service manages **user registration, deregistration, and user state validation** for the ENCS 691K Cloud-Native Auction System.

It enforces system rules related to user participation in auctions and maintains user identity records.

**Port:** 3001 (configurable via `PORT` environment variable)

---

## Responsibilities

- Register new users with unique username constraint
- Deregister users when system constraints allow
- Validate deregistration rules:
  - Rule 1: User cannot deregister if currently selling an item
  - Rule 2: User cannot deregister if they are the highest bidder in an active auction
- Maintain user identity and status metadata
- Provide internal endpoints for Auction/Bid service communication

---

## Architecture & Design

### Storage

Uses **in-memory storage** for demo/development purposes. Each user has:

```javascript
{
  id: string (UUID),
  username: string (unique),
  email: string,
  isActive: boolean,
  isSelling: boolean,           // Synced with Auction Service
  isHighestBidder: boolean,     // Synced with Bid Service
  createdAt: Date,
  deregisteredAt: Date (optional)
}
```

**For production:** Replace in-memory storage with persistent database (PostgreSQL, MongoDB, etc.)

### ENCS 691K Specification Mapping

- **UC1 - User Registration:** POST `/users/register` enforces unique username constraint
- **UC2 - User Deregistration:** POST `/users/deregister` validates both deregistration rules before allowing removal
- **Service Communication:** Internal endpoints for Auction/Bid service integration (see TODOs)

---

## API Endpoints

### 1. Register New User

**Endpoint:** `POST /users/register`

**Description:** Create a new user account with unique username.

**Request Body:**
```json
{
  "username": "john_doe",
  "email": "john@example.com"
}
```

**Success Response (201 Created):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "username": "john_doe",
  "email": "john@example.com",
  "isActive": true,
  "createdAt": "2026-01-30T10:00:00.000Z"
}
```

**Error Response (400 Bad Request - Username exists):**
```json
{
  "error": "Username already exists"
}
```

**Error Response (400 Bad Request - Missing fields):**
```json
{
  "error": "Username and email are required"
}
```

**Integration Notes:**
- TODO: Notify Auth Service to create login credentials
- TODO: Query Notification Service to send welcome email

---

### 2. Deregister User

**Endpoint:** `POST /users/deregister`

**Description:** Remove user from system if constraints are satisfied.

**Specification:** ENCS 691K UC2 - User Deregistration with Rule Enforcement

**Request Body:**
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Success Response (200 OK):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "username": "john_doe",
  "isActive": false,
  "deregisteredAt": "2026-01-30T10:05:00.000Z"
}
```

**Error Response (409 Conflict - User is selling):**
```json
{
  "error": "Cannot deregister: user is currently selling an item",
  "reason": "active_seller"
}
```

**How to fix:**
- User must wait for all auctions to complete or cancel listings
- Query Auction Service to identify active listings

**Error Response (409 Conflict - User is highest bidder):**
```json
{
  "error": "Cannot deregister: user is highest bidder in active auctions",
  "reason": "active_bidder",
  "affectedAuctions": [
    "auction-001",
    "auction-002"
  ]
}
```

**How to fix:**
- User must be outbid in all active auctions or auctions must complete
- Query Bid Service to identify active bids

**Error Response (404 Not Found):**
```json
{
  "error": "User not found"
}
```

**Error Response (400 Bad Request - Already deregistered):**
```json
{
  "error": "User is already deregistered"
}
```

**Integration Requirements:**
- TODO: Before checking `isSelling`, query Auction Service:
  - `GET /auctions/user/{userId}/active` - returns list of active listings
  - Update user's `isSelling` status based on response
- TODO: Before checking `isHighestBidder`, query Bid Service:
  - `GET /bids/user/{userId}/active-highest` - returns auctions where user is highest bidder
  - Update user's `isHighestBidder` status based on response

---

### 3. Get User by ID

**Endpoint:** `GET /users/{userId}`

**Description:** Retrieve user information.

**Path Parameters:**
- `userId` (string): User UUID

**Success Response (200 OK):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "username": "john_doe",
  "email": "john@example.com",
  "isActive": true,
  "isSelling": false,
  "isHighestBidder": false,
  "createdAt": "2026-01-30T10:00:00.000Z"
}
```

**Error Response (404 Not Found):**
```json
{
  "error": "User not found"
}
```

---

### 4. List All Users

**Endpoint:** `GET /users`

**Description:** Get all registered users (for debugging/demo).

**Success Response (200 OK):**
```json
{
  "users": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "username": "john_doe",
      "email": "john@example.com",
      "isActive": true
    },
    {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "username": "jane_smith",
      "email": "jane@example.com",
      "isActive": true
    }
  ]
}
```

---

### 5. Update User Seller Status (Internal)

**Endpoint:** `POST /users/{userId}/update-seller-status`

**Description:** Internal endpoint called by Auction Service when user starts/stops selling.

**Path Parameters:**
- `userId` (string): User UUID

**Request Body:**
```json
{
  "isSelling": true
}
```

**Success Response (200 OK):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "isSelling": true
}
```

**Security Note:** TODO - Implement service-to-service authentication (service account JWT or API key)

---

### 6. Update User Bidder Status (Internal)

**Endpoint:** `POST /users/{userId}/update-bidder-status`

**Description:** Internal endpoint called by Bid Service when user wins/loses bid status.

**Path Parameters:**
- `userId` (string): User UUID

**Request Body:**
```json
{
  "isHighestBidder": true
}
```

**Success Response (200 OK):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "isHighestBidder": true
}
```

**Security Note:** TODO - Implement service-to-service authentication (service account JWT or API key)

---

### 7. Health Check

**Endpoint:** `GET /health`

**Description:** Health check for orchestration/monitoring systems.

**Success Response (200 OK):**
```json
{
  "status": "User Service is healthy"
}
```

---

## Quick Start

### Prerequisites
- Java 17+ installed
- Maven 3.9+ installed
- Port 3001 available (or set `PORT` environment variable)

### Installation & Running

```bash
# Build
mvn clean package -DskipTests

# Run
java -jar target/user-service-0.0.1-SNAPSHOT.jar
```

### Testing Endpoints

**Register a user:**
```bash
curl -X POST http://localhost:3001/users/register \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "email": "alice@example.com"}'
```

**List all users:**
```bash
curl http://localhost:3001/users
```

**Get specific user:**
```bash
curl http://localhost:3001/users/550e8400-e29b-41d4-a716-446655440000
```

**Deregister user (will succeed if no constraints violated):**
```bash
curl -X POST http://localhost:3001/users/deregister \
  -H "Content-Type: application/json" \
  -d '{"userId": "550e8400-e29b-41d4-a716-446655440000"}'
```

---

## Security Considerations

### Current Implementation
- ✅ Unique username enforced
- ✅ In-memory storage (safe for demo)
- ⚠️ **No authentication required** - any client can call endpoints

### Production Requirements
- [ ] Add JWT token validation (require Authorization header)
- [ ] Use Auth Service middleware to protect public endpoints
- [ ] Hash/encrypt sensitive data
- [ ] Use persistent database instead of in-memory storage
- [ ] Implement rate limiting
- [ ] Add request validation and sanitization
- [ ] Use HTTPS/TLS for all communications
- [ ] Implement audit logging

---

## Integration Points (TODOs)

### With Auction Service
- **Problem:** Need to verify user is not actively selling before deregistration
- **Solution:** Query Auction Service endpoint:
  - `GET /auctions/user/{userId}/active`
  - Response indicates if user has active listings
  - Update `user.isSelling` flag based on result

**Code Location:** `UserController.java` (Deregistration handler)

### With Bid Service
- **Problem:** Need to verify user is not highest bidder before deregistration
- **Solution:** Query Bid Service endpoint:
  - `GET /bids/user/{userId}/active-highest`
  - Response lists auctions where user is highest bidder
  - Update `user.isHighestBidder` flag based on result
  - Return affected auction IDs in error response

**Code Location:** `UserController.java` (Deregistration handler)

### With Auth Service
- **Problem:** New user registration doesn't create login credentials
- **Solution:** Call Auth Service to create credentials:
  - `POST /auth/register` with username and temporary password
  - TODO: Decide on password initialization strategy

**Code Location:** `UserController.java` (Register endpoint)

### With Notification Service
- **Problem:** Users don't receive welcome/deregistration emails
- **Solution:** Call Notification Service:
  - `POST /notifications/email` to send welcome on registration
  - `POST /notifications/email` to send confirmation on deregistration

**Code Location:** `UserController.java` (Register/Deregister handlers)

---

## Data Model

### User Record

```
User {
  id: UUID                  - Unique identifier
  username: string          - Unique username (constraint)
  email: string             - Email address
  isActive: boolean         - Account status
  isSelling: boolean        - Currently selling items (synced with Auction Service)
  isHighestBidder: boolean  - Currently highest bidder (synced with Bid Service)
  createdAt: Date           - Account creation timestamp
  deregisteredAt: Date      - Account deregistration timestamp (if applicable)
}
```

---

## Environment Variables

```bash
PORT=3001                    # Port to listen on (default: 3001)
```

---

## File Structure

```
user-service/
├── pom.xml                # Maven dependencies
├── src/main/java/...      # Controllers and models
├── src/main/resources/    # application.properties
├── Dockerfile             # Docker container definition
└── README.md              # This file
```

---

## Future Enhancements

1. **Database Integration** - Replace in-memory storage with PostgreSQL/MongoDB
2. **Advanced Validation** - Email verification, phone number validation
3. **User Profiles** - Support address, payment info, ratings
4. **Audit Logging** - Log all user operations for compliance
5. **Soft Deletes** - Instead of hard delete, mark users as deleted
6. **User Roles** - Admin, seller, buyer role differentiation
7. **Account Recovery** - Password reset, account reactivation

---

## Notes

- This service is **stateless and horizontally scalable** once database is added
- Current in-memory storage is **lost on service restart**
- Deregistration validation requires **real-time communication** with Auction and Bid services
- TODO: Implement circuit breaker pattern for service-to-service calls
