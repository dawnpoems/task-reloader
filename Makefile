.PHONY: check check-web check-api

check: check-api check-web

check-web:
	cd apps/web && npm ci && npm run type-check && npm run lint && npm run build

check-api:
	./gradlew :apps:api:test
