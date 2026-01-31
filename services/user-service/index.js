/**
 * User Service - Identity Layer
 * ENCS 691K: Cloud-Native Auction System
 * 
 * Specification Mapping:
 * - UC1: User Registration (POST /users/register)
 * - UC2: User Deregistration (POST /users/deregister)
 * - Rule Enforcement: Deregistration rules check seller status and bid status
 * - Service Communication: Placeholder TODOs for integration with Auction/Bid services
 */

const express = require('express');
const { v4: uuidv4 } = require('uuid');

const app = express();
const PORT = process.env.PORT || 3001;

// ============================================================================
// IN-MEMORY STORAGE (Placeholder Persistence)
// ============================================================================
// In production, this would use a database. For demo purposes, we use in-memory.
// Users are stored with their metadata and status.
const users = new Map();

/**
 * User object structure:
 * {
 *   id: string (UUID),
 *   username: string (unique),
 *   email: string,
 *   createdAt: Date,
 *   isActive: boolean,
 *   isSelling: boolean (TODO: Query from Auction Service),
 *   isHighestBidder: boolean (TODO: Query from Bid Service)
 * }
 */

// ============================================================================
// MIDDLEWARE
// ============================================================================
app.use(express.json());

// Simple request logging
app.use((req, res, next) => {
  console.log(`[${new Date().toISOString()}] ${req.method} ${req.path}`);
  next();
});

// ============================================================================
// ENDPOINTS
// ============================================================================

/**
 * POST /users/register
 * 
 * Register a new user in the system.
 * 
 * ENCS 691K Specification: UC1 - User Registration
 * - Ensures unique username constraint
 * - Creates user identity record
 * 
 * Request body:
 * {
 *   "username": "john_doe",
 *   "email": "john@example.com"
 * }
 * 
 * Response (201 Created):
 * {
 *   "id": "550e8400-e29b-41d4-a716-446655440000",
 *   "username": "john_doe",
 *   "email": "john@example.com",
 *   "isActive": true,
 *   "createdAt": "2026-01-30T10:00:00.000Z"
 * }
 * 
 * Response (400 Bad Request):
 * {
 *   "error": "Username already exists"
 * }
 */
app.post('/users/register', (req, res) => {
  try {
    const { username, email } = req.body;

    // Validation
    if (!username || !email) {
      return res.status(400).json({ error: 'Username and email are required' });
    }

    // Check for duplicate username (UC1: Unique username constraint)
    const existingUser = Array.from(users.values()).find(u => u.username === username);
    if (existingUser) {
      return res.status(400).json({ error: 'Username already exists' });
    }

    // Create new user
    const userId = uuidv4();
    const newUser = {
      id: userId,
      username,
      email,
      isActive: true,
      isSelling: false, // TODO: Initialize from Auction Service
      isHighestBidder: false, // TODO: Initialize from Bid Service
      createdAt: new Date()
    };

    users.set(userId, newUser);

    console.log(`User registered: ${username} (${userId})`);

    res.status(201).json({
      id: newUser.id,
      username: newUser.username,
      email: newUser.email,
      isActive: newUser.isActive,
      createdAt: newUser.createdAt
    });
  } catch (error) {
    console.error('Registration error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

/**
 * POST /users/deregister
 * 
 * Deregister a user from the system.
 * 
 * ENCS 691K Specification: UC2 - User Deregistration with Rule Enforcement
 * - Deregistration rule 1: Cannot deregister if selling an item
 * - Deregistration rule 2: Cannot deregister if highest bidder in active auction
 * 
 * The system must validate these constraints by querying:
 * - Auction Service: Check if user has active listings
 * - Bid Service: Check if user is highest bidder in any active auction
 * 
 * Request body:
 * {
 *   "userId": "550e8400-e29b-41d4-a716-446655440000"
 * }
 * 
 * Success Response (200 OK):
 * {
 *   "id": "550e8400-e29b-41d4-a716-446655440000",
 *   "username": "john_doe",
 *   "isActive": false,
 *   "deregisteredAt": "2026-01-30T10:05:00.000Z"
 * }
 * 
 * Error Response (409 Conflict) - Cannot deregister while selling:
 * {
 *   "error": "Cannot deregister: user is currently selling an item",
 *   "reason": "active_seller"
 * }
 * 
 * Error Response (409 Conflict) - Cannot deregister while highest bidder:
 * {
 *   "error": "Cannot deregister: user is highest bidder in active auctions",
 *   "reason": "active_bidder",
 *   "affectedAuctions": ["auction-id-1", "auction-id-2"]
 * }
 * 
 * Error Response (404 Not Found):
 * {
 *   "error": "User not found"
 * }
 */
app.post('/users/deregister', (req, res) => {
  try {
    const { userId } = req.body;

    // Validation
    if (!userId) {
      return res.status(400).json({ error: 'userId is required' });
    }

    // Find user
    const user = users.get(userId);
    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }

    if (!user.isActive) {
      return res.status(400).json({ error: 'User is already deregistered' });
    }

    // ENCS 691K UC2: Deregistration Rule 1 - Check if user is selling
    // TODO: Query Auction Service at endpoint /auctions/user/{userId}/active
    // to get list of active auctions where this user is the seller
    if (user.isSelling) {
      return res.status(409).json({
        error: 'Cannot deregister: user is currently selling an item',
        reason: 'active_seller'
      });
    }

    // ENCS 691K UC2: Deregistration Rule 2 - Check if user is highest bidder
    // TODO: Query Bid Service at endpoint /bids/user/{userId}/active-highest-bids
    // to get list of auctions where this user is the highest bidder
    if (user.isHighestBidder) {
      return res.status(409).json({
        error: 'Cannot deregister: user is highest bidder in active auctions',
        reason: 'active_bidder',
        affectedAuctions: [] // TODO: Populate from Bid Service
      });
    }

    // Perform deregistration
    user.isActive = false;
    user.deregisteredAt = new Date();

    console.log(`User deregistered: ${user.username} (${userId})`);

    res.status(200).json({
      id: user.id,
      username: user.username,
      isActive: user.isActive,
      deregisteredAt: user.deregisteredAt
    });
  } catch (error) {
    console.error('Deregistration error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

/**
 * GET /users/{userId}
 * 
 * Retrieve user information by ID.
 * 
 * Path parameter:
 * - userId: string (UUID)
 * 
 * Response (200 OK):
 * {
 *   "id": "550e8400-e29b-41d4-a716-446655440000",
 *   "username": "john_doe",
 *   "email": "john@example.com",
 *   "isActive": true,
 *   "isSelling": false,
 *   "isHighestBidder": false,
 *   "createdAt": "2026-01-30T10:00:00.000Z"
 * }
 * 
 * Response (404 Not Found):
 * {
 *   "error": "User not found"
 * }
 */
app.get('/users/:userId', (req, res) => {
  try {
    const { userId } = req.params;
    const user = users.get(userId);

    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }

    res.status(200).json({
      id: user.id,
      username: user.username,
      email: user.email,
      isActive: user.isActive,
      isSelling: user.isSelling,
      isHighestBidder: user.isHighestBidder,
      createdAt: user.createdAt
    });
  } catch (error) {
    console.error('Get user error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

/**
 * GET /users
 * 
 * List all users (for debugging/demo purposes).
 * 
 * Response (200 OK):
 * {
 *   "users": [
 *     {
 *       "id": "550e8400-e29b-41d4-a716-446655440000",
 *       "username": "john_doe",
 *       "email": "john@example.com",
 *       "isActive": true
 *     }
 *   ]
 * }
 */
app.get('/users', (req, res) => {
  try {
    const userList = Array.from(users.values()).map(user => ({
      id: user.id,
      username: user.username,
      email: user.email,
      isActive: user.isActive
    }));

    res.status(200).json({ users: userList });
  } catch (error) {
    console.error('List users error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

/**
 * POST /users/{userId}/update-seller-status
 * 
 * Internal endpoint to update user's seller status.
 * Called by Auction Service when user starts/stops selling.
 * 
 * ENCS 691K: Service-to-service communication for constraint validation.
 * 
 * TODO: Protect with service-to-service authentication
 * 
 * Request body:
 * {
 *   "isSelling": true
 * }
 */
app.post('/users/:userId/update-seller-status', (req, res) => {
  try {
    const { userId } = req.params;
    const { isSelling } = req.body;

    const user = users.get(userId);
    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }

    user.isSelling = isSelling;
    res.status(200).json({ id: user.id, isSelling: user.isSelling });
  } catch (error) {
    console.error('Update seller status error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

/**
 * POST /users/{userId}/update-bidder-status
 * 
 * Internal endpoint to update user's highest bidder status.
 * Called by Bid Service when user wins or loses an auction bid.
 * 
 * ENCS 691K: Service-to-service communication for constraint validation.
 * 
 * TODO: Protect with service-to-service authentication
 * 
 * Request body:
 * {
 *   "isHighestBidder": true
 * }
 */
app.post('/users/:userId/update-bidder-status', (req, res) => {
  try {
    const { userId } = req.params;
    const { isHighestBidder } = req.body;

    const user = users.get(userId);
    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }

    user.isHighestBidder = isHighestBidder;
    res.status(200).json({ id: user.id, isHighestBidder: user.isHighestBidder });
  } catch (error) {
    console.error('Update bidder status error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

/**
 * Health check endpoint
 */
app.get('/health', (req, res) => {
  res.status(200).json({ status: 'User Service is healthy' });
});

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
  console.log(`User Service listening on port ${PORT}`);
  console.log(`Health check: http://localhost:${PORT}/health`);
});

module.exports = app;
