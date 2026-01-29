# Authentication & Security Service

## Purpose
The Auth Service provides **authentication and authorization** for the Auction System.

It ensures that only authenticated users can access protected services.

---

## Responsibilities
- Authenticate users
- Issue and validate JWT tokens
- Enforce role-based access control
- Secure inter-service communication

---

## Security Mechanisms
- JWT-based authentication
- Token validation filters
- HTTPS/TLS enforced

---

## APIs
- `POST /auth/login`
- `POST /auth/validate`

---

## Notes
This service centralizes security logic and reduces duplication across microservices.
