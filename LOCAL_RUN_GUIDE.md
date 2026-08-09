# Run BalanceTrail locally

This is the shortest path for Windows, including a machine whose installed JDK is 26.

## Prerequisite

Install and start Docker Desktop. Confirm that Linux containers are enabled.

Your installed JDK 26 is not used by this workflow. The backend compiles and runs inside the Java 21 images declared in `backend/Dockerfile`.

## Start

Open PowerShell in the extracted `balance-trail` directory:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Wait until the backend and database health checks pass, then open:

- Dashboard: <http://localhost:3000>
- API documentation: <http://localhost:8080/swagger-ui.html>
- Health: <http://localhost:8080/actuator/health>

Default local demo login:

```text
username: analyst
password: change-me-now
```

Upload `backend/src/main/resources/demo/gateway-transactions.csv` to see matched, mismatched, missing, invalid, and duplicate results.

## Stop

```powershell
docker compose down
```

This preserves PostgreSQL and uploaded-file volumes. To intentionally erase local demo data and start fresh:

```powershell
docker compose down -v
```

The `-v` command deletes only this Compose project's named database/upload volumes. Do not use it when you want to preserve prior runs.

## Common Windows issues

### Port already in use

The project uses ports 3000, 8080, and 5432. Stop the process/container already using the conflicting port or adjust the host-side port in `docker-compose.yml`.

### Docker daemon unavailable

Start Docker Desktop and wait until its engine reports ready, then rerun the Compose command.

### Old database credentials after editing `.env`

PostgreSQL initializes credentials only when its volume is first created. If this is disposable local data, run `docker compose down -v` and start again.

### Running without Docker

Use JDK 21 for the backend so it matches CI and the verified runtime. Start PostgreSQL with Compose, run `backend/mvnw.cmd spring-boot:run`, then run `npm ci` and `npm run dev` inside `frontend`. Full details are in `README.md`.
