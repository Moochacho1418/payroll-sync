# payroll-sync

A small Kotlin service that keeps a local list of workers in line with a payroll provider's API.
It is a pattern study of the parts of a payroll integration that tend to break in production:
expiring tokens, flaky provider APIs, duplicate syncs, and writes nobody needed.

## What it does

- **Caches the provider's OAuth token** (`TokenProvider`) and refreshes it a minute before it
  expires, so a request never leaves with a token about to die. Concurrent callers share one
  refresh.
- **Reads every page of workers** (`WorkerClient`), retrying rate limits and server errors with
  exponential backoff, refreshing the token once on a 401, and failing fast on other client errors.
- **Syncs only what changed** (`SyncService`). New workers are created, changed ones updated,
  missing ones deactivated rather than deleted, and unchanged ones left alone. Running the same
  sync twice writes nothing the second time.
- **Runs one sync per organization at a time** (`MySqlLockService`), even with several service
  instances pulling jobs off the same queue.

## Why the lock lives in MySQL

The service already has a MySQL connection, so `GET_LOCK` gives a lock every instance can see
without adding Redis locks, ZooKeeper, or another moving part. The lock belongs to the connection
that took it, so the service holds that one connection for the whole run and releases on the same
connection. If an instance crashes mid-sync, MySQL drops the connection and the lock goes with it,
so an organization can never get stuck locked.

## Run it

```
./gradlew test   # unit tests; MySQL lock tests run when PAYROLL_SYNC_MYSQL_URL is set
./gradlew run    # three syncs against a fake provider, printing what each one changed
```

Needs JDK 17.
