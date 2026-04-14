# Running The Product

This guide documents the current local run flow for the Auction System from the `main` branch.

## Prerequisites
- Docker
- Java and Maven
- Node.js and npm
- Git

## 1. Switch to `main`
```bash
cd auction
git checkout main
git pull origin main
```

## 2. Start PostgreSQL and Redis
If the containers already exist:
```bash
docker start auction-postgres auction-redis
```

If they do not exist yet:
```bash
docker run --name auction-postgres \
  -e POSTGRES_USER=auction \
  -e POSTGRES_PASSWORD=auction \
  -e POSTGRES_DB=auction \
  -p 5432:5432 -d postgres:16-alpine

docker run --name auction-redis -p 6379:6379 -d redis:7-alpine
```

## 3. Initialize the database
If `psql` is not installed locally, use the Postgres Docker image to apply the schema:
```bash
docker run --rm -i \
  -v "$(pwd)/scripts/init-db.sql:/init-db.sql" \
  postgres:16-alpine \
  psql "postgresql://auction:auction@host.docker.internal:5432/auction" -f /init-db.sql
```

## 4. Start backend services
Open a separate terminal for each service.

### User Service
```bash
cd auction/services/user-service
mvn spring-boot:run
```

### Auth Service
```bash
cd auction/services/auth-service
mvn spring-boot:run
```

### Auction Service
```bash
cd auction/services/auction-service
DB_URL=jdbc:postgresql://localhost:5432/auction DB_USER=auction DB_PASSWORD=auction mvn spring-boot:run
```

### Bid Service
```bash
cd auction/services/bid-service
DB_URL=jdbc:postgresql://localhost:5432/auction DB_USER=auction DB_PASSWORD=auction REDIS_HOST=localhost REDIS_PORT=6379 mvn spring-boot:run
```

### Notification Service
```bash
cd auction/services/notification-service
mvn spring-boot:run
```

## 5. Start the frontend
```bash
cd auction/frontend
npm install
npm run dev
```

## 6. Open the application
- Frontend: [http://localhost:5173](http://localhost:5173)

## 7. Health checks
Use these to confirm all services are up:
- [http://localhost:3001/health](http://localhost:3001/health)
- [http://localhost:3002/health](http://localhost:3002/health)
- [http://localhost:3003/health](http://localhost:3003/health)
- [http://localhost:3004/health](http://localhost:3004/health)
- [http://localhost:3005/health](http://localhost:3005/health)

## 8. Optional smoke test
```bash
cd auction/scripts
./prototype-smoke-test.sh
```

## Notes
- The current local demo configuration uses a `30-second` auction duration by default for faster testing and presentation.
- PostgreSQL is the source of truth for persisted data.
- Redis is used for highest-bid caching in the bid service.
- The frontend runs on Vite and connects directly to services on ports `3001` through `3005`.
