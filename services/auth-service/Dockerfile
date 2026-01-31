# Stage 1: Build stage
FROM node:18-alpine AS builder

WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm ci --only=production

# Stage 2: Runtime stage
FROM node:18-alpine

# Labels for documentation
LABEL service="auth-service"
LABEL description="Auth Service - ENCS 691K Access Control Layer"
LABEL version="1.0.0"

# Set working directory
WORKDIR /app

# Copy dependencies from builder stage
COPY --from=builder /app/node_modules ./node_modules

# Copy application code
COPY package*.json ./
COPY index.js ./

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD node -e "require('http').get('http://localhost:3002/health', (r) => {if (r.statusCode !== 200) throw new Error(r.statusCode)})"

# Expose port
EXPOSE 3002

# Set environment variables
ENV NODE_ENV=production
ENV PORT=3002
ENV JWT_SECRET=${JWT_SECRET:-demo-secret-key-change-in-production}
ENV JWT_EXPIRY=24h

# Run application
CMD ["node", "index.js"]
