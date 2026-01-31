# Auth Service - Access Control Layer

## Overview

The Auth Service provides **authentication and authorization** for the ENCS 691K Cloud-Native Auction System.

It ensures that only authenticated users can access protected services through JWT-based token validation.

**Port:** 3002 (configurable via `PORT` environment variable)

---

## Responsibilities

- Authenticate users with username and password
- Issue JWT tokens for authenticated sessions
- Validate tokens and provide token verification endpoints
- Export authentication middleware for protecting downstream services
- Manage token lifecycle (issuing, validation, expiration)

---

## Architecture & Design

### Authentication Strategy

**JWT (JSON Web Tokens)** for stateless, scalable authentication:
- No server-side session storage required
- Token includes user identity and metadata
- Tokens are signed with secret key for integrity verification
- Tokens include expiration time for security

### Token Structure

JWT tokens include:
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "john_doe",
  "iat": 1706595600,
  "exp": 1706682000
}
```

### ENCS 691K Specification Mapping

- **Authentication:** POST `/auth/login` validates credentials and issues JWT tokens
- **Authorization:** POST `/auth/validate` and GET `/auth/verify` verify tokens for protected access
- **Middleware:** `authenticateToken` middleware exported for use in other services
- **Token Lifecycle:** Configurable expiration via `JWT_EXPIRY` environment variable

---

## API Endpoints

### 1. User Login

**Endpoint:** `POST /auth/login`

**Description:** Authenticate user and issue JWT token.

**ENCS 691K Specification:** User Authentication

**Request Body:**
```json
{
  "username": "john_doe",
  "password": "password123"
}
```

**Success Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOiI1NTBlODQwMC1lMjliLTQxZDQtYTcxNi00NDY2NTU0NDAwMDAiLCJ1c2VybmFtZSI6ImpvaG5fZG9lIiwiaWF0IjoxNzA2NTk1NjAwLCJleHAiOjE3MDY2ODIwMDB9.abcd1234...",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "john_doe",
  "expiresIn": "24h"
}
```

**Error Response (401 Unauthorized - Invalid credentials):**
```json
{
  "error": "Invalid username or password"
}
```

**Error Response (400 Bad Request):**
```json
{
  "error": "Username and password are required"
}
```

**Demo Credentials:**
- Username: `john_doe`, Password: `password123`
- Username: `jane_smith`, Password: `securepass456`

**Usage in Downstream Requests:**
```bash
# Include token in Authorization header
curl http://localhost:3001/users \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

**Integration Notes:**
- TODO: Query User Service to fetch user credentials and validate password hash
- TODO: Use bcrypt or argon2 for secure password hashing in production

---

### 2. Validate Token

**Endpoint:** `POST /auth/validate`

**Description:** Validate a JWT token without requiring request context.

**ENCS 691K Specification:** Token Validation for service-to-service authorization

**Request Body:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Success Response (200 OK):**
```json
{
  "valid": true,
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "john_doe",
  "expiresAt": 1706682000
}
```

**Error Response (401 Unauthorized - Invalid/Expired):**
```json
{
  "valid": false,
  "error": "Invalid or expired token",
  "details": "jwt expired"
}
```

**Error Response (400 Bad Request):**
```json
{
  "error": "Token is required"
}
```

**Use Case:**
- Auction Service receives request with token in header
- Auction Service calls `/auth/validate` to verify token is valid
- If valid, process request; if invalid, return 401

---

### 3. Verify Current Token

**Endpoint:** `GET /auth/verify`

**Description:** Verify current user's token (token in Authorization header).

**ENCS 691K Specification:** Token verification for current request context

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Success Response (200 OK):**
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "john_doe",
  "expiresAt": 1706682000
}
```

**Error Response (401 Unauthorized - No token):**
```json
{
  "error": "Access token required"
}
```

**Error Response (403 Forbidden - Invalid token):**
```json
{
  "error": "Invalid or expired token",
  "details": "jwt malformed"
}
```

---

### 4. Protected Example Endpoint

**Endpoint:** `GET /auth/protected-example`

**Description:** Example protected endpoint demonstrating authentication middleware.

**ENCS 691K Specification:** Authorization enforcement example

**Request Headers:**
```
Authorization: Bearer <valid-token>
```

**Success Response (200 OK):**
```json
{
  "message": "You have accessed protected resource",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "john_doe"
}
```

**Error Response (401/403):** See `/auth/verify` error responses

---

### 5. Health Check

**Endpoint:** `GET /health`

**Description:** Health check for orchestration/monitoring systems.

**Success Response (200 OK):**
```json
{
  "status": "Auth Service is healthy"
}
```

---

## Using Authentication Middleware

### For Protecting Downstream Services

The Auth Service exports `authenticateToken` middleware for use in other services:

**Example in Auction Service (pseudo-code):**
```javascript
const { authenticateToken } = require('auth-service');
const app = express();

// Protect public endpoints
app.post('/auctions', authenticateToken, (req, res) => {
  // req.user contains decoded token: { userId, username, iat, exp }
  const userId = req.user.userId;
  // ... create auction for this user
});

// Protect seller operations
app.get('/auctions/my-listings', authenticateToken, (req, res) => {
  const userId = req.user.userId;
  // ... get auctions created by this user
});
```

### Middleware Behavior

```javascript
const authenticateToken = (req, res, next) => {
  // 1. Extract token from Authorization header
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1]; // Get "Bearer <token>"

  // 2. Return 401 if no token
  if (!token) {
    return res.status(401).json({ error: 'Access token required' });
  }

  // 3. Verify token signature and expiration
  jwt.verify(token, JWT_SECRET, (err, user) => {
    if (err) {
      // Return 403 if token invalid/expired
      return res.status(403).json({ error: 'Invalid or expired token' });
    }

    // 4. Attach decoded token to request
    req.user = user; // { userId, username, iat, exp }
    next(); // Continue to next middleware/handler
  });
};
```

---

## Quick Start

### Prerequisites
- Node.js 14+ installed
- Port 3002 available (or set `PORT` environment variable)

### Installation & Running

```bash
# Install dependencies
npm install

# Start the service
npm start

# Or with auto-reload (requires nodemon)
npm run dev
```

### Testing Endpoints

**Login and get token:**
```bash
curl -X POST http://localhost:3002/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "john_doe", "password": "password123"}'
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "john_doe",
  "expiresIn": "24h"
}
```

**Validate token:**
```bash
curl -X POST http://localhost:3002/auth/validate \
  -H "Content-Type: application/json" \
  -d '{"token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."}'
```

**Access protected endpoint:**
```bash
curl http://localhost:3002/auth/verify \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

## Configuration

### Environment Variables

```bash
# Port the Auth Service listens on (default: 3002)
PORT=3002

# JWT secret key for signing tokens (CHANGE IN PRODUCTION!)
JWT_SECRET=demo-secret-key-change-in-production

# Token expiration time (default: 24h)
# Supports: '2h', '1d', 86400 (seconds), etc.
JWT_EXPIRY=24h
```

### Production Configuration

**⚠️ CRITICAL SECURITY ISSUES:**

1. **JWT Secret:** Generate a strong, random secret
   ```bash
   node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
   ```

2. **Password Storage:** Credentials are currently stored in plain text
   - TODO: Implement bcrypt/argon2 password hashing
   - Store hashed passwords in database
   - Never store plain text passwords

3. **HTTPS/TLS:** All communications must be encrypted
   - Configure HTTPS on Auth Service
   - Require HTTPS for token transmission

---

## Security Considerations

### Current Implementation
- ✅ JWT tokens with signature verification
- ✅ Token expiration enforcement
- ✅ 401/403 status codes for auth failures
- ⚠️ **Plain text demo credentials** (not production-ready)
- ⚠️ **Hardcoded JWT secret** (must change in production)

### Production Requirements
- [ ] Use bcrypt/argon2 for password hashing
- [ ] Store credentials in secure database
- [ ] Use environment variables for JWT secret
- [ ] Implement HTTPS/TLS
- [ ] Add rate limiting on login endpoint
- [ ] Implement token refresh mechanism
- [ ] Add audit logging for authentication attempts
- [ ] Implement CORS properly for cross-origin requests
- [ ] Use secure cookie settings if using cookies instead of headers

---

## Integration Points (TODOs)

### With User Service
- **Problem:** Credentials stored locally in Auth Service (not scalable)
- **Solution:** Query User Service for user data
  - `GET /users/{userId}` to fetch user by ID
  - Store/validate credentials on User Service
  - Keep Auth Service stateless

**Code Location:** `index.js` line ~105 (Login endpoint)

### With External Identity Providers
- **Problem:** No OAuth 2.0 / OIDC support
- **Solution:** Implement OAuth flows
  - Support Google, GitHub, etc. login
  - Integrate with SAML for enterprise
  - Provide federated identity option

**Future Enhancement:** Not required for ENCS 691K but good for production

### With Rate Limiting Service
- **Problem:** No protection against brute force attacks
- **Solution:** Query Rate Limiting Service
  - Track login attempts per username
  - Block after N failed attempts
  - Return 429 Too Many Requests

**Code Location:** `index.js` line ~105 (Login endpoint)

---

## Data Model

### JWT Payload

```
Token Payload {
  userId: string        - User UUID (identifies user)
  username: string      - User's username
  iat: number          - Issued At (Unix timestamp)
  exp: number          - Expiration time (Unix timestamp)
}
```

### User Credentials (Demo Storage)

```
Credentials {
  username: string      - Unique username
  password: string      - User password (TODO: Hash in production)
  userId: string        - Reference to user in User Service
}
```

---

## File Structure

```
auth-service/
├── index.js               # Main application file with authentication logic
├── package.json           # Dependencies and scripts
├── Dockerfile             # Docker container definition
└── README.md              # This file
```

---

## Future Enhancements

1. **Password Hashing** - Use bcrypt/argon2 for secure password storage
2. **Database Integration** - Move credentials to PostgreSQL/MongoDB
3. **Refresh Tokens** - Implement token refresh mechanism for better UX
4. **OAuth 2.0 Support** - Google, GitHub, Facebook login integration
5. **SAML Support** - Enterprise single sign-on integration
6. **Rate Limiting** - Prevent brute force attacks on login
7. **Audit Logging** - Log all authentication events for compliance
8. **Multi-Factor Authentication** - Add 2FA/MFA support
9. **API Key Management** - Support service-to-service API keys
10. **Certificate-Based Auth** - Support mTLS for service communication

---

## Notes

- Auth Service is **stateless** - no server-side session storage
- Tokens can be validated **independently** by any service
- Token expiration is **enforced at validation time**
- This service is **horizontally scalable** - same secret key across all instances
- Token **revocation is not supported** (stateless design) - TODO: Implement token blacklist if needed

---

## Common Issues & Troubleshooting

### Issue: "Invalid or expired token"
**Cause:** Token signature verification failed or token has expired
**Solution:** 
- Verify JWT_SECRET is the same across all services
- Check system clock is synchronized
- Verify token hasn't exceeded expiration time

### Issue: "Access token required"
**Cause:** Authorization header missing or malformed
**Solution:**
- Ensure header format is: `Authorization: Bearer <token>`
- Verify token is extracted correctly after "Bearer " prefix

### Issue: Different tokens generated for same login
**Cause:** Token includes `iat` (issued at) timestamp which varies per request
**Solution:** This is expected behavior. Tokens are unique per login.

---

## Performance Considerations

- JWT validation is **fast** - just signature verification and expiration check
- No database lookups required for token validation
- Suitable for **high-throughput scenarios** with many concurrent users
- Token size is **small** (~200-400 bytes) for efficient transmission
