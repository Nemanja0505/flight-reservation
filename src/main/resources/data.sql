-- Seed data for the flights table.
-- Runs after Hibernate creates the schema (spring.jpa.defer-datasource-initialization=true).
-- H2-compatible syntax: RANDOM_UUID() for ids, DATEADD/NOW() for relative departure times.

-- FR101: DUB -> LHR. Normal bookable flight with only 3 seats so seat
-- exhaustion (SeatNotAvailableException) can be triggered quickly.
INSERT INTO flights (id, flight_number, origin_airport_code, destination_airport_code, departure_time, origin_timezone, total_seats, available_seats, active)
VALUES (RANDOM_UUID(), 'FR101', 'DUB', 'LHR', DATEADD('HOUR', 6, NOW()), 'Europe/Dublin', 3, 3, TRUE);

-- BA202: LHR -> CDG. Normal bookable flight with plenty of seats.
INSERT INTO flights (id, flight_number, origin_airport_code, destination_airport_code, departure_time, origin_timezone, total_seats, available_seats, active)
VALUES (RANDOM_UUID(), 'BA202', 'LHR', 'CDG', DATEADD('HOUR', 8, NOW()), 'Europe/London', 50, 50, TRUE);

-- AF303: CDG -> BER. Departs in 30 minutes, inside the 45-minute booking
-- window, so all booking attempts must be rejected.
INSERT INTO flights (id, flight_number, origin_airport_code, destination_airport_code, departure_time, origin_timezone, total_seats, available_seats, active)
VALUES (RANDOM_UUID(), 'AF303', 'CDG', 'BER', DATEADD('MINUTE', 30, NOW()), 'Europe/Paris', 50, 50, TRUE);

-- LH404: BER -> DUB. Inactive flight: must not appear in GET /flights and
-- booking against it must be rejected.
INSERT INTO flights (id, flight_number, origin_airport_code, destination_airport_code, departure_time, origin_timezone, total_seats, available_seats, active)
VALUES (RANDOM_UUID(), 'LH404', 'BER', 'DUB', DATEADD('HOUR', 4, NOW()), 'Europe/Berlin', 50, 50, FALSE);

-- EI505: DUB -> CDG. Used to exercise the confirm and cancel flows.
INSERT INTO flights (id, flight_number, origin_airport_code, destination_airport_code, departure_time, origin_timezone, total_seats, available_seats, active)
VALUES (RANDOM_UUID(), 'EI505', 'DUB', 'CDG', DATEADD('HOUR', 10, NOW()), 'Europe/Dublin', 50, 50, TRUE);

-- VY606: BCN -> FCO. Used to test hold expiry: manually update a booking's
-- expires_at in the H2 console to simulate a hold older than 15 minutes,
-- then verify the scheduler releases it.
INSERT INTO flights (id, flight_number, origin_airport_code, destination_airport_code, departure_time, origin_timezone, total_seats, available_seats, active)
VALUES (RANDOM_UUID(), 'VY606', 'BCN', 'FCO', DATEADD('HOUR', 12, NOW()), 'Europe/Madrid', 50, 50, TRUE);
