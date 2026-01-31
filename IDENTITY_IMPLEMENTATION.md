# ENCS 691K Identity & Access Layer - Implementation Guide

## Overview

This document provides a quick reference for the implemented identity and access control layer for the cloud-native auction system.

---

## Project Structure

```
services/
├── user-service/              ← Identity Management
│   ├── index.js              # User registration/deregistration logic
│   ├── package.json          # Dependencies: express, uuid
│   ├── Dockerfile            # Multi-stage build with health checks
│   └── README.md             # Comprehensive API documentation
│
└── auth-service/              ← Access Control
    ├── index.js              # JWT authentication & validation
    ├── package.json          # Dependencies: express, jsonwebtoken
    ├── Dockerfile            # Multi-stage build with health checks
    └── README.md             # Complete API documentation
```

---

## Quick Start

### User Service (Port 3001)

```bash
cd services/user-service
npm install
npm start
```

**Test Registration:**
```bash
curl -X POST http://localhost:3001/users/register \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "email": "alice@example.com"}'
```

### Auth Service (Port 3002)

```bash
cd services/auth-service
npm install
npm start
```

**Test Login:**
```bash
curl -X POST http://localhost:3002/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "john_doe", "password": "password123"}'
```

---

## Specification Mapping (ENCS 691K)

### User Service

| Requirement | Implementation | File | Location |
|---|---|---|---|
| **UC1: User Registration** | POST `/users/register` enforces unique username | index.js | Lines 56-87 |
| **UC2: User Deregistration** | POST `/users/deregister` with rule validation | index.js | Lines 135-183 |
| **Rule 1: Not Selling** | Check `user.isSelling` before deregistration | index.js | Lines 159-167 |
| **Rule 2: Not Highest Bidder** | Check `user.isHighestBidder` before deregistration | index.js | Lines 169-177 |
| **Service Communication** | Internal endpoints for status updates | index.js | Lines 242-301 |

### Auth Service

| Requirement | Implementation | File | Location |
|---|---|---|---|
| **User Login** | POST `/auth/login` with credential validation | index.js | Lines 79-128 |
| **Token Issuance** | JWT tokens with userId, username, exp | index.js | Lines 117-120 |
| **Token Validation** | POST `/auth/validate` for independent verification | index.js | Lines 130-162 |
| **Auth Middleware** | `authenticateToken` for protecting endpoints | index.js | Lines 47-75 |
| **Protected Example** | GET `/auth/protected-example` with middleware | index.js | Lines 180-192 |

---

## Key Features Implemented

### User Service ✅

- ✅ User registration with unique username validation
- ✅ User deregistration with constraints:
  - Cannot deregister if selling items
  - Cannot deregister if highest bidder
- ✅ In-memory user storage with UUID identifiers
- ✅ User status tracking (active, selling, bidding)
- ✅ Internal endpoints for service communication
- ✅ Health check endpoint
- ✅ Comprehensive error handling
- ✅ ENCS 691K specification comments throughout

### Auth Service ✅

- ✅ User login with password validation
- ✅ JWT token generation and signing
- ✅ Token validation endpoint (POST)
- ✅ Token verification middleware
- ✅ Protected endpoint example
- ✅ Token expiration enforcement
- ✅ In-memory demo credentials
- ✅ Health check endpoint
- ✅ Comprehensive error handling
- ✅ ENCS 691K specification comments throughout

### DevOps ✅

- ✅ Docker multi-stage builds for minimal image size
- ✅ Health checks for both services
- ✅ Environment variable configuration
- ✅ Proper port exposure (3001, 3002)
- ✅ Production-ready base images (Alpine)

---

## API Quick Reference

### User Service (Port 3001)

```bash
# Register user
curl -X POST http://localhost:3001/users/register \
  -H "Content-Type: application/json" \
  -d '{"username": "bob", "email": "bob@example.com"}'

# Get user
curl http://localhost:3001/users/550e8400-e29b-41d4-a716-446655440000

# List all users
curl http://localhost:3001/users

# Deregister (if allowed)
curl -X POST http://localhost:3001/users/deregister \
  -H "Content-Type: application/json" \
  -d '{"userId": "550e8400-e29b-41d4-a716-446655440000"}'
```

### Auth Service (Port 3002)

```bash
# Login
TOKEN=$(curl -s -X POST http://localhost:3002/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "john_doe", "password": "password123"}' \
  | jq -r '.token')

# Validate token
curl -X POST http://localhost:3002/auth/validate \
  -H "Content-Type: application/json" \
  -d "{\"token\": \"$TOKEN\"}"

# Verify token (using Authorization header)
curl http://localhost:3002/auth/verify \
  -H "Authorization: Bearer $TOKEN"

# Access protected endpoint
curl http://localhost:3002/auth/protected-example \
  -H "Authorization: Bearer $TOKEN"
```

---

## Demo Credentials

**Auth Service includes demo credentials:**
- Username: `john_doe` / Password: `password123`
- Username: `jane_smith` / Password: `securepass456`

---

## Integration TODOs (Next Steps)

### User Service Integration Points

1. **With Auction Service**
   - Query `/auctions/user/{userId}/active` to check if user is selling
   - Update user status via `/users/{userId}/update-seller-status`
   - Location: index.js lines 145-157

2. **With Bid Service**
   - Query `/bids/user/{userId}/active-highest` to check highest bidder status
   - Update user status via `/users/{userId}/update-bidder-status`
   - Location: index.js lines 159-169

3. **With Auth Service**
   - Create login credentials when new user registers
   - Location: index.js line 112

4. **With Notification Service**
   - Send welcome email on registration
   - Send deregistration confirmation email
   - Location: index.js lines 112, 155

### Auth Service Integration Points

1. **With User Service**
   - Fetch user credentials from User Service instead of local storage
   - Validate passwords against User Service records
   - Location: index.js line 105

2. **With Rate Limiting Service** (Future)
   - Prevent brute force attacks on login
   - Location: index.js line 105

---

## Security Checklist

### ✅ Implemented
- [x] JWT-based stateless authentication
- [x] Token signature verification
- [x] Token expiration enforcement
- [x] Unique username constraint
- [x] Proper HTTP status codes (401, 403, 409)
- [x] Error handling without information leakage
- [x] Health check endpoints

### ⚠️ TODO for Production
- [ ] Use bcrypt/argon2 for password hashing
- [ ] Store credentials in database
- [ ] Use environment variables for JWT secret
- [ ] Implement HTTPS/TLS
- [ ] Add rate limiting on login
- [ ] Implement token refresh mechanism
- [ ] Add audit logging
- [ ] Implement token revocation/blacklist
- [ ] Use secure cookie settings
- [ ] Add request validation/sanitization

---

## File Size & Dependencies

### User Service
- **index.js:** ~300 lines
- **Dependencies:** express (4.18.2), uuid (9.0.0)
- **Image Size:** ~150MB (Node 18 Alpine base)

### Auth Service
- **index.js:** ~350 lines
- **Dependencies:** express (4.18.2), jsonwebtoken (9.1.0)
- **Image Size:** ~150MB (Node 18 Alpine base)

---

## Testing Checklist

```
User Service Tests:
☐ POST /users/register - create new user
☐ POST /users/register - duplicate username rejected
☐ GET /users/{userId} - get user by ID
☐ GET /users - list all users
☐ POST /users/deregister - success when no constraints
☐ POST /users/deregister - fail when selling
☐ POST /users/deregister - fail when highest bidder
☐ POST /users/{userId}/update-seller-status - update status
☐ POST /users/{userId}/update-bidder-status - update status
☐ GET /health - health check

Auth Service Tests:
☐ POST /auth/login - successful login
☐ POST /auth/login - invalid credentials
☐ POST /auth/validate - valid token
☐ POST /auth/validate - expired token
☐ POST /auth/validate - invalid token
☐ GET /auth/verify - with valid token
☐ GET /auth/verify - without token
☐ GET /auth/protected-example - with valid token
☐ GET /auth/protected-example - without token
☐ GET /health - health check
```

---

## Deployment Options

### Option 1: Local Development
```bash
cd services/user-service && npm start &
cd services/auth-service && npm start &
```

### Option 2: Docker Build
```bash
docker build -t user-service services/user-service
docker build -t auth-service services/auth-service

docker run -p 3001:3001 user-service
docker run -p 3002:3002 auth-service
```

### Option 3: Docker Compose (for future integration)
```yaml
version: '3.8'
services:
  user-service:
    build: ./services/user-service
    ports:
      - "3001:3001"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:3001/health"]
      
  auth-service:
    build: ./services/auth-service
    ports:
      - "3002:3002"
    environment:
      JWT_SECRET: ${JWT_SECRET}
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:3002/health"]
```

---

## Architecture Diagram

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │
       │ 1. Register
       ▼
┌─────────────────────┐
│  User Service       │ (Port 3001)
│ ┌─────────────────┐ │
│ │ In-Memory Users │ │
│ │ (UUID-indexed)  │ │
│ └─────────────────┘ │
└─────────────────────┘
       │
       │ 2. Login
       ▼
┌─────────────────────┐
│  Auth Service       │ (Port 3002)
│ ┌─────────────────┐ │
│ │ JWT Generation  │ │
│ │ Token Validator │ │
│ └─────────────────┘ │
└─────────────────────┘
       │
       │ 3. Protected API Call
       ▼
┌─────────────────────┐
│ Other Services      │
│ (Auction, Bid, etc) │
│ (With Auth Header)  │
└─────────────────────┘
```

---

## ENCS 691K Mapping Summary

This implementation addresses the identity and access control requirements of the ENCS 691K course:

- **Identity Layer (User Service)**
  - UC1: User Registration with unique constraints
  - UC2: User Deregistration with business rule enforcement
  - State management for user participation (selling, bidding)

- **Access Control Layer (Auth Service)**
  - JWT-based stateless authentication
  - Token validation for protected resources
  - Middleware for microservice protection

- **Architecture**
  - Stateless services for horizontal scalability
  - Service-to-service communication patterns
  - Separation of concerns (identity vs access)

---

## Next Steps for Integration

1. **Integrate with Auction Service**
   - Implement endpoints for querying active listings
   - Call User Service status update endpoint

2. **Integrate with Bid Service**
   - Implement endpoints for highest bidder queries
   - Call User Service status update endpoint

3. **Add Database Layer**
   - Replace in-memory storage with PostgreSQL
   - Implement proper credential storage with hashing

4. **Enhance Security**
   - Add rate limiting
   - Implement token refresh mechanism
   - Add audit logging
   - HTTPS/TLS configuration

5. **Production Hardening**
   - Environment variable management
   - Secrets rotation strategy
   - Monitoring and alerting
   - Load balancing configuration

---

## Contact & Support

For issues or questions about the implementation:
- Review README.md files in each service directory
- Check inline code comments referencing ENCS 691K specifications
- Refer to API endpoint documentation sections

---

**Implementation Date:** January 30, 2026  
**Status:** Complete and Demo-Ready ✅
