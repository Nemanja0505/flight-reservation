# Flight Seat Reservation System

A REST API for searching flights, holding seats, and confirming reservations.

## Overview

The Flight Seat Reservation System lets clients browse available flights, place a
temporary hold on a seat, and confirm or cancel that hold. It is built around a
two-phase booking model:

1. **Hold** — `POST /flights/{id}/bookings` reserves a seat and creates a booking
   in `HELD` status. The seat is decremented immediately, but the hold is not
   permanent.
2. **Confirm** — `POST /bookings/{id}/confirm` promotes a `HELD` booking to
   `CONFIRMED`. Unconfirmed holds expire automatically after 15 minutes and the
   seat is returned to the flight.

Administrators manage the flight catalogue through `/admin` endpoints. The system
enforces a configurable booking cut-off before departure (resolved in the
flight's local timezone), serialises concurrent seat writes with a pessimistic
database lock, and uses soft deletes so historical booking data is never lost.

**Tech stack:** Java 21 · Spring Boot 3.4.x · Maven · H2 (in-memory) · Spring
Data JPA · MapStruct · Lombok.

## How to Run

### Prerequisites

- **Java 21** (JDK)
- **Maven 3.9+**

### Run locally

```bash
mvn spring-boot:run
```

The application starts on **http://localhost:8080**. On startup it creates the
schema and seeds six demo flights from `src/main/resources/data.sql`.

> Flight IDs are generated fresh on every startup (the seed data uses
> `RANDOM_UUID()`). Call `GET /flights` first to discover the IDs you need for
> the booking and admin examples below.

### H2 console

The in-memory database can be inspected at **http://localhost:8080/h2-console**:

| Field        | Value                    |
|--------------|--------------------------|
| JDBC URL     | `jdbc:h2:mem:flightdb`   |
| User Name    | `sa`                     |
| Password     | *(leave blank)*          |

### Run with Docker

Build the image:

```bash
docker build -t flight-reservation .
```

Run the container:

```bash
docker run --rm -p 8080:8080 flight-reservation
```

The API is then available on **http://localhost:8080** exactly as above.

## API Reference

Base URL: **`http://localhost:8080`**

All request and response bodies are JSON. Errors share a common envelope:

```json
{
  "code": "ERROR_CODE",
  "message": "Human-readable description",
  "timestamp": "2026-05-22T12:00:00Z",
  "errors": []
}
```

| HTTP status | `code`              | Meaning                                            |
|-------------|---------------------|----------------------------------------------------|
| 400         | `VALIDATION_ERROR`  | Request body failed bean validation                |
| 404         | `FLIGHT_NOT_FOUND`  | No active flight with that id                      |
| 404         | `BOOKING_NOT_FOUND` | No booking with that id                            |
| 409         | `SEAT_NOT_AVAILABLE`| Flight is sold out, or that seat is already taken  |
| 422         | `BOOKING_NOT_ALLOWED` | Booking window closed / illegal state transition |
| 500         | `INTERNAL_ERROR`    | Unexpected server error                            |

---

### `GET /flights`

Lists active flights. All query parameters are optional and can be combined:
`origin`, `destination` (IATA codes), and `date` (`YYYY-MM-DD`). Inactive
(soft-deleted) flights are never returned.

**Request**

```bash
curl "http://localhost:8080/flights?origin=DUB&destination=LHR"
```

**Response — `200 OK`**

```json
[
  {
    "id": "3f1a9c5e-7b22-4d8e-9a01-2c4e6f8a0b13",
    "flightNumber": "FR101",
    "originAirportCode": "DUB",
    "destinationAirportCode": "LHR",
    "departureTime": "2026-05-23T02:30:00",
    "originTimezone": "Europe/Dublin",
    "totalSeats": 3,
    "availableSeats": 3
  }
]
```

An empty result set returns `200 OK` with `[]` — not a 404.

---

### `GET /flights/{id}`

Fetches a single active flight.

**Request**

```bash
curl http://localhost:8080/flights/3f1a9c5e-7b22-4d8e-9a01-2c4e6f8a0b13
```

**Response — `200 OK`**

```json
{
  "id": "3f1a9c5e-7b22-4d8e-9a01-2c4e6f8a0b13",
  "flightNumber": "FR101",
  "originAirportCode": "DUB",
  "destinationAirportCode": "LHR",
  "departureTime": "2026-05-23T02:30:00",
  "originTimezone": "Europe/Dublin",
  "totalSeats": 3,
  "availableSeats": 2
}
```

**Error — `404 Not Found`**

```json
{
  "code": "FLIGHT_NOT_FOUND",
  "message": "Flight not found with id: 00000000-0000-0000-0000-000000000000",
  "timestamp": "2026-05-22T12:00:00Z",
  "errors": []
}
```

---

### `POST /admin/flights`

Creates a flight. `availableSeats` is initialised from `totalSeats` and the
flight is created as `active`. `departureTime` is interpreted as local time at the origin airport (see *Booking window* below). 

**Request**

```bash
curl -X POST http://localhost:8080/admin/flights \
  -H 'Content-Type: application/json' \
  -d '{
    "flightNumber": "FR999",
    "originAirportCode": "DUB",
    "destinationAirportCode": "JFK",
    "departureTime": "2026-12-01T09:00:00",
    "originTimezone": "Europe/Dublin",
    "totalSeats": 180
  }'
```

**Response — `201 Created`** (with a `Location: /flights/{id}` header)

```json
{
  "id": "b7e4d2a1-8c33-4f9b-a012-5d7f9b1c3e24",
  "flightNumber": "FR999",
  "originAirportCode": "DUB",
  "destinationAirportCode": "JFK",
  "departureTime": "2026-12-01T09:00:00",
  "originTimezone": "Europe/Dublin",
  "totalSeats": 180,
  "availableSeats": 180
}
```

**Error — `400 Bad Request`** (e.g. a 4-letter airport code)

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "timestamp": "2026-05-22T12:00:00Z",
  "errors": [
    { "field": "originAirportCode", "message": "size must be between 3 and 3" }
  ]
}
```

---

### `DELETE /admin/flights/{id}`

Soft-deletes a flight by setting `active = false`. A flight that has any
`CONFIRMED` bookings cannot be deactivated.

**Request**

```bash
curl -i -X DELETE http://localhost:8080/admin/flights/b7e4d2a1-8c33-4f9b-a012-5d7f9b1c3e24
```

**Response — `204 No Content`** (empty body)

**Error — `422 Unprocessable Entity`** (flight has confirmed bookings)

```json
{
  "code": "BOOKING_NOT_ALLOWED",
  "message": "Cannot delete flight with confirmed bookings",
  "timestamp": "2026-05-22T12:00:00Z",
  "errors": []
}
```

**Error — `404 Not Found`**

```json
{
  "code": "FLIGHT_NOT_FOUND",
  "message": "Flight not found with id: 00000000-0000-0000-0000-000000000000",
  "timestamp": "2026-05-22T12:00:00Z",
  "errors": []
}
```

---

### `POST /flights/{id}/bookings`

Holds a seat on a flight. The booking is created in `HELD` status, the flight's
`availableSeats` is decremented, and an `expiresAt` timestamp is set 15 minutes
out. The request is rejected if the flight is sold out, if the requested seat
is already held or confirmed on the same flight, or if the booking window has
closed.

**Request**

```bash
curl -X POST http://localhost:8080/flights/3f1a9c5e-7b22-4d8e-9a01-2c4e6f8a0b13/bookings \
  -H 'Content-Type: application/json' \
  -d '{
    "passengerName": "Ada Lovelace",
    "passengerEmail": "ada@example.com",
    "seatNumber": "12A"
  }'
```

**Response — `201 Created`** (with a `Location: /bookings/{id}` header)

```json
{
  "id": "c9f1e3b2-4a55-4d77-b234-6e8a0c2d4f36",
  "flightId": "3f1a9c5e-7b22-4d8e-9a01-2c4e6f8a0b13",
  "flightNumber": "FR101",
  "passengerName": "Ada Lovelace",
  "passengerEmail": "ada@example.com",
  "seatNumber": "12A",
  "status": "HELD",
  "createdAt": "2026-05-22T12:00:00Z",
  "expiresAt": "2026-05-22T12:15:00Z"
}
```

**Error — `409 Conflict`** (flight sold out or seat already held/confirmed)

```json
{
  "code": "SEAT_NOT_AVAILABLE",
  "message": "No seats available on this flight",
  "timestamp": "2026-05-22T12:00:00Z",
  "errors": []
}
```

**Error — `422 Unprocessable Entity`** (booking window closed)

```json
{
  "code": "BOOKING_NOT_ALLOWED",
  "message": "Booking closed. Flight AF303 departs at 2026-05-22T12:30+02:00[Europe/Paris], cutoff was 2026-05-22T11:45+02:00[Europe/Paris]",
  "timestamp": "2026-05-22T12:00:00Z",
  "errors": []
}
```

**Error — `400 Bad Request`** (invalid email / blank fields)

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "timestamp": "2026-05-22T12:00:00Z",
  "errors": [
    { "field": "passengerEmail", "message": "must be a well-formed email address" }
  ]
}
```

---

### `GET /bookings/{id}`

Fetches a single booking in any status (`HELD`, `CONFIRMED`, `CANCELLED`,
`EXPIRED`). See *EXPIRED vs CANCELLED* below for the distinction between the
last two.

**Request**

```bash
curl http://localhost:8080/bookings/c9f1e3b2-4a55-4d77-b234-6e8a0c2d4f36
```

**Response — `200 OK`**

```json
{
  "id": "c9f1e3b2-4a55-4d77-b234-6e8a0c2d4f36",
  "flightId": "3f1a9c5e-7b22-4d8e-9a01-2c4e6f8a0b13",
  "flightNumber": "FR101",
  "passengerName": "Ada Lovelace",
  "passengerEmail": "ada@example.com",
  "seatNumber": "12A",
  "status": "EXPIRED",
  "createdAt": "2026-05-22T12:00:00Z",
  "expiresAt": "2026-05-22T12:15:00Z"
}
```

---

### `POST /bookings/{id}/confirm`

Promotes a `HELD` booking to `CONFIRMED`. Only `HELD` bookings can be confirmed —
an already-confirmed, cancelled, or expired booking is rejected.

**Request**

```bash
curl -X POST http://localhost:8080/bookings/c9f1e3b2-4a55-4d77-b234-6e8a0c2d4f36/confirm
```

**Response — `200 OK`**

```json
{
  "id": "c9f1e3b2-4a55-4d77-b234-6e8a0c2d4f36",
  "flightId": "3f1a9c5e-7b22-4d8e-9a01-2c4e6f8a0b13",
  "flightNumber": "FR101",
  "passengerName": "Ada Lovelace",
  "passengerEmail": "ada@example.com",
  "seatNumber": "12A",
  "status": "CONFIRMED",
  "createdAt": "2026-05-22T12:00:00Z",
  "expiresAt": "2026-05-22T12:15:00Z"
}
```

**Error — `422 Unprocessable Entity`** (booking is not `HELD`, or the hold has expired)

```json
{
  "code": "BOOKING_NOT_ALLOWED",
  "message": "Only HELD bookings can be confirmed. Current status: CANCELLED",
  "timestamp": "2026-05-22T12:00:00Z",
  "errors": []
}
```

**Error — `404 Not Found`**

```json
{
  "code": "BOOKING_NOT_FOUND",
  "message": "Booking not found with id: 00000000-0000-0000-0000-000000000000",
  "timestamp": "2026-05-22T12:00:00Z",
  "errors": []
}
```

---

### `DELETE /bookings/{id}`

Cancels a booking. The booking moves to `CANCELLED` and its seat is returned to
the flight. An already-cancelled booking is rejected.

**Request**

```bash
curl -i -X DELETE http://localhost:8080/bookings/c9f1e3b2-4a55-4d77-b234-6e8a0c2d4f36
```

**Response — `204 No Content`** (empty body)

**Error — `422 Unprocessable Entity`** (already cancelled or expired)

```json
{
  "code": "BOOKING_NOT_ALLOWED",
  "message": "Booking is already cancelled",
  "timestamp": "2026-05-22T12:00:00Z",
  "errors": []
}
```

**Error — `404 Not Found`**

```json
{
  "code": "BOOKING_NOT_FOUND",
  "message": "Booking not found with id: 00000000-0000-0000-0000-000000000000",
  "timestamp": "2026-05-22T12:00:00Z",
  "errors": []
}
```

## Key Design Decisions

### Concurrency — Pessimistic locking on Flight

Seat booking is a critical write where conflicts are *expected* under load: many
clients race for the last few seats on the same flight. Booking creation acquires
a `PESSIMISTIC_WRITE` lock on the `Flight` row (`findActiveByIdWithLock` — with
the `active = true` filter, so closed flights cannot be booked even under race),
so concurrent booking attempts on the same flight are serialised at the database
level — each transaction reads the true `availableSeats`, decrements it, and
commits before the next one proceeds.

Cancellations and hold expiry use a sibling query, `findByIdWithLock`, which
drops the `active` filter. A flight may have been soft-deleted between the
booking being created and the seat being released; the counter must still be
incremented consistently. `Flight.releaseOneSeat()` additionally clamps the new
value to `totalSeats` as a defensive invariant against any double-release bug.

Optimistic locking was rejected here. It would let two transactions read the same
seat count, then fail one at commit time with an `OptimisticLockException`,
pushing retry logic onto every client. Because contention is the normal case (not
the exception), serialising up front is both simpler and more predictable than
detecting the collision after the fact and retrying.

### Booking window — Timezone-aware

Bookings close 45 minutes before departure, measured in the flight's *local*
time. A flight stores its departure as a wall-clock `LocalDateTime` plus an
`originTimezone` in IANA format (e.g. `Europe/Dublin`). At booking time the
service resolves both "now" and the departure into that zone, subtracts the
window, and compares:

> A DUB flight departing **14:30 Europe/Dublin** stops accepting bookings at
> **13:45 Dublin time** — correctly, even when the server's own clock runs in UTC.

Storing an IANA zone (rather than a fixed offset) means the cut-off stays correct
across daylight-saving transitions.

### Seat holds — Scheduler-based TTL

A `HELD` booking is a temporary reservation. If it is not confirmed within 15
minutes it must not block the seat forever. `HoldExpiryScheduler` runs every 60
seconds, delegates to `HoldExpiryProcessor`, which finds `HELD` bookings whose
`expiresAt` is in the past, sets them to `EXPIRED`, and returns each seat to
its flight's `availableSeats`.

`POST /bookings/{id}/confirm` also re-checks `expiresAt` against the current
clock and rejects the request if the hold is past its expiry. The scheduler
runs at most every 60 seconds, so an expired hold can briefly survive in the
database in `HELD` status; the explicit check closes that window — an expired
hold can never be confirmed even if the sweeper has not yet swept it.

Both numbers are configuration, not constants:

```yaml
booking:
  hold-duration-minutes: 15            # how long a hold survives
  hold-expiry-check-interval-ms: 60000 # how often the sweeper runs
```

### Soft delete for flights

Flights are never hard-deleted. `DELETE /admin/flights/{id}` sets `active = false`
instead. This preserves historical bookings and referential integrity — a
cancelled flight that still has past bookings remains a valid foreign-key target
and an auditable record. Inactive flights are filtered out of every read path, so
they disappear from `GET /flights` without the data disappearing from the
database. A flight that still has `CONFIRMED` bookings cannot be deactivated at
all.

As part of the same transaction, any bookings still in `HELD` status on that
flight are moved to `CANCELLED`. Leaving open holds on a withdrawn flight would
let a passenger confirm a booking on a flight that no longer exists from the
catalogue's perspective.

### H2 for simplicity

The project uses an in-memory H2 database so it is fully self-contained and
runnable with a single command — no external infrastructure, no setup. Schema is
created on startup (`ddl-auto: create-drop`) and demo data is seeded from
`data.sql`.

To switch to **PostgreSQL** for a real deployment:

1. Replace the H2 dependency with `org.postgresql:postgresql`.
2. Point `spring.datasource.*` at the Postgres instance.
3. Change `spring.jpa.hibernate.ddl-auto` to `validate`.
4. Introduce **Flyway** or **Liquibase** to own the schema as versioned
   migrations.

## Trade-offs & Limitations

- **Hold expiry lag** — an expired hold can linger for up to 60 seconds (the
  scheduler interval) before its seat is released. The explicit `expiresAt`
  check in `confirmBooking` prevents confirmation inside that window, but the
  seat does briefly remain unavailable to new bookings.
- **Scheduler is not distributed** — running multiple application instances would
  make every instance process the same expired holds. Fix: a distributed lock
  (**ShedLock**) or a `claimed_at` column so only one worker picks up each row.
- **Hold-expiry batch is not paginated** — `HoldExpiryProcessor` loads every
  expired hold into memory and processes them in a single transaction. Fine
  for the demo's data volume, but a high-volume deployment would need paginated
  batch processing and shorter transactions to avoid
  long lock-holds on the `flights` table.
- **Seat number is a free-text field** — uniqueness is enforced in the service
  layer (`existsByFlightIdAndSeatNumberAndStatusIn`), made safe under
  concurrency by the pessimistic `Flight` lock.
- **No authentication or authorisation** — the `/admin` endpoints are open to
  anyone who can reach the service.
- **No pagination** — `GET /flights` returns the entire result set in one
  response.
- **No notifications** — confirming or cancelling a booking sends no email or
  other message to the passenger.

## What Would Be Added in Production

- **PostgreSQL + Flyway** migrations in place of in-memory H2.
- **Spring Security + JWT** for authentication, with role-based access control
  guarding the `/admin` endpoints.
- **Distributed locking for the scheduler** (ShedLock) so hold expiry runs
  exactly once across a multi-instance deployment.
- **Database-level seat uniqueness** via a partial unique index on
  `(flight_id, seat_number) WHERE status IN ('HELD', 'CONFIRMED')`. The service
  check is correct under the pessimistic lock today, but a DB-level constraint
  is the right place for that invariant once the schema is owned by versioned
  migrations.
- **Pagination and sorting** on `GET /flights`.
- **Structured (JSON) logging** with correlation IDs for request tracing.
- **Email notifications** to passengers on booking confirmation and cancellation.