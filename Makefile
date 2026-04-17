COMPOSE ?= docker compose

.PHONY: compose-up compose-down compose-logs compose-dev validate-k8s

compose-up:
	$(COMPOSE) up --build -d

compose-down:
	$(COMPOSE) down

compose-logs:
	$(COMPOSE) logs -f

compose-dev:
	$(COMPOSE) --profile dev up --build frontend-dev user-service auth-service auction-service bid-service notification-service postgres postgres-init redis

validate-k8s:
	kubectl apply --dry-run=client -k infra/kubernetes
