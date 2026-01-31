/**
 * Authentication & Authorization Service - Access Control Layer
 * ENCS 691K: Cloud-Native Auction System
 * 
 * Specification Mapping:
 * - Authentication: JWT-based user authentication
 * - Authorization: Token validation for protected endpoints
 * - Service Integration: Middleware for protecting downstream services
 * 
 * This service provides:
 * 1. User login endpoint - validates credentials and issues JWT tokens
 * 2. Token validation endpoint - validates JWT tokens from client requests
 * 3. Authentication middleware - can be used by other services
 */

const express = require('express');
const jwt = require('jsonwebtoken');

const app = express();
const PORT = process.env.PORT || 3002;

// ============================================================================
// CONFIGURATION
// ============================================================================
// In production, use environment variables and secure key storage
const JWT_SECRET = process.env.JWT_SECRET || 'demo-secret-key-change-in-production';
const JWT_EXPIRY = process.env.JWT_EXPIRY || '24h';

console.log('⚠️  WARNING: Using default JWT secret. Change JWT_SECRET environment variable in production!');

// ============================================================================
// IN-MEMORY USER CREDENTIALS (Demo/Testing Only)
// ============================================================================
// In production, credentials would be stored in a database with hashed passwords.
// This is for demo purposes only.
const credentials = new Map();

// Initialize with demo user
credentials.set('john_doe', {
  password: 'password123', // TODO: Use bcrypt/argon2 in production
  userId: '550e8400-e29b-41d4-a716-446655440000'
});

credentials.set('jane_smith', {
  password: 'securepass456',
  userId: '550e8400-e29b-41d4-a716-446655440001'
});

// ============================================================================
// MIDDLEWARE
// ============================================================================
app.use(express.json());

// Request logging
app.use((req, res, next) => {
  console.log(`[${new Date().toISOString()}] ${req.method} ${req.path}`);
  next();
});

// ============================================================================
// AUTHENTICATION MIDDLEWARE
// ============================================================================

/**
 * JWT Authentication Middleware
 * 
 * Extracts JWT token from Authorization header (Bearer token format).
 * Validates token signature and expiry.
 * 
 * ENCS 691K: Authorization enforcement for protected endpoints.
 * 
 * Usage:
 *   app.get('/protected-route', authenticateToken, (req, res) => { ... });
 * 
 * Token is expected in header:
 *   Authorization: Bearer <token>
 * 
 * On success:
 *   - req.user object populated with decoded token payload
 * 
 * On failure:
 *   - 401 Unauthorized if token missing
 *   - 403 Forbidden if token invalid/expired
 */
const authenticateToken = (req, res, next) => {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1]; // Extract token after "Bearer "

  if (!token) {
    return res.status(401).json({ error: 'Access token required' });
  }

  jwt.verify(token, JWT_SECRET, (err, user) => {
    if (err) {
      console.log('Token verification failed:', err.message);
      return res.status(403).json({ 
        error: 'Invalid or expired token',
        details: err.message
      });
    }

    req.user = user; // Attach user info to request
    next();
  });
};

// ============================================================================
// ENDPOINTS
// ============================================================================

/**
 * POST /auth/login
 * 
 * Authenticate user and issue JWT token.
 * 
 * ENCS 691K Specification: User Authentication
 * - Validates username and password
 * - Issues JWT token with user identity
 * - Token includes userId and username in payload
 * 
 * Request body:
 * {
 *   "username": "john_doe",
 *   "password": "password123"
 * }
 * 
 * Success Response (200 OK):
 * {
 *   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
 *   "userId": "550e8400-e29b-41d4-a716-446655440000",
 *   "username": "john_doe",
 *   "expiresIn": "24h"
 * }
 * 
 * Error Response (401 Unauthorized):
 * {
 *   "error": "Invalid username or password"
 * }
 * 
 * Usage in downstream requests:
 * - Add header: Authorization: Bearer <token>
 * - Token can be validated using /auth/validate endpoint
 * 
 * Demo credentials:
 * - Username: john_doe, Password: password123
 * - Username: jane_smith, Password: securepass456
 */
app.post('/auth/login', (req, res) => {
  try {
    const { username, password } = req.body;

    // Validation
    if (!username || !password) {
      return res.status(400).json({ error: 'Username and password are required' });
    }

    // TODO: In production, query User Service to get user by username
    // and use bcrypt.compare() to validate password hash
    const user = credentials.get(username);
    
    if (!user || user.password !== password) {
      return res.status(401).json({ error: 'Invalid username or password' });
    }

    // Create JWT token
    const payload = {
      userId: user.userId,
      username: username,
      iat: Math.floor(Date.now() / 1000) // issued at
    };

    const token = jwt.sign(payload, JWT_SECRET, { expiresIn: JWT_EXPIRY });

    console.log(`User logged in: ${username}`);

    res.status(200).json({
      token,
      userId: user.userId,
      username,
      expiresIn: JWT_EXPIRY
    });
  } catch (error) {
    console.error('Login error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

/**
 * POST /auth/validate
 * 
 * Validate a JWT token without requiring request context.
 * Useful for other services to validate tokens independently.
 * 
 * ENCS 691K: Token validation for service-to-service authorization.
 * 
 * Request body:
 * {
 *   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
 * }
 * 
 * Success Response (200 OK):
 * {
 *   "valid": true,
 *   "userId": "550e8400-e29b-41d4-a716-446655440000",
 *   "username": "john_doe",
 *   "expiresAt": 1706691600
 * }
 * 
 * Error Response (401 Unauthorized):
 * {
 *   "valid": false,
 *   "error": "Invalid or expired token"
 * }
 */
app.post('/auth/validate', (req, res) => {
  try {
    const { token } = req.body;

    if (!token) {
      return res.status(400).json({ error: 'Token is required' });
    }

    jwt.verify(token, JWT_SECRET, (err, decoded) => {
      if (err) {
        return res.status(401).json({
          valid: false,
          error: 'Invalid or expired token',
          details: err.message
        });
      }

      res.status(200).json({
        valid: true,
        userId: decoded.userId,
        username: decoded.username,
        expiresAt: decoded.exp
      });
    });
  } catch (error) {
    console.error('Validation error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

/**
 * GET /auth/protected-example
 * 
 * Example protected endpoint demonstrating authenticateToken middleware.
 * 
 * This endpoint requires valid JWT token in Authorization header.
 * 
 * ENCS 691K: Authorization enforcement example.
 * 
 * Request:
 *   GET /auth/protected-example
 *   Authorization: Bearer <token>
 * 
 * Success Response (200 OK):
 * {
 *   "message": "You have accessed protected resource",
 *   "userId": "550e8400-e29b-41d4-a716-446655440000",
 *   "username": "john_doe"
 * }
 * 
 * Error Response (401/403): See authenticateToken middleware
 */
app.get('/auth/protected-example', authenticateToken, (req, res) => {
  res.status(200).json({
    message: 'You have accessed protected resource',
    userId: req.user.userId,
    username: req.user.username
  });
});

/**
 * GET /auth/verify
 * 
 * Verify current user's token (token in Authorization header).
 * 
 * ENCS 691K: Token verification for current request context.
 * 
 * Request:
 *   GET /auth/verify
 *   Authorization: Bearer <token>
 * 
 * Success Response (200 OK):
 * {
 *   "userId": "550e8400-e29b-41d4-a716-446655440000",
 *   "username": "john_doe",
 *   "expiresAt": 1706691600
 * }
 */
app.get('/auth/verify', authenticateToken, (req, res) => {
  res.status(200).json({
    userId: req.user.userId,
    username: req.user.username,
    expiresAt: req.user.exp
  });
});

/**
 * Health check endpoint
 */
app.get('/health', (req, res) => {
  res.status(200).json({ status: 'Auth Service is healthy' });
});

// ============================================================================
// MIDDLEWARE EXPORT
// ============================================================================
// Export middleware for use in other services
app.authenticateToken = authenticateToken;

// ============================================================================
// ERROR HANDLING
// ============================================================================
app.use((err, req, res, next) => {
  console.error('Unhandled error:', err);
  res.status(500).json({ error: 'Internal server error' });
});

// ============================================================================
// START SERVER
// ============================================================================
app.listen(PORT, () => {
  console.log(`Auth Service listening on port ${PORT}`);
  console.log(`Health check: http://localhost:${PORT}/health`);
  console.log(`Demo login endpoint: http://localhost:${PORT}/auth/login`);
});

module.exports = { app, authenticateToken };
