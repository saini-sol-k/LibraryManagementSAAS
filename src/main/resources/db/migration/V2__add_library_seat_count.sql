-- ============================================================
-- LIBRARY SEAT COUNT
--
-- How many seats a library is configured to have. Set when the customer is
-- onboarded, and adjustable afterwards by SUPER_ADMIN or the organization
-- owner.
--
-- The column is the source of truth. Seat rows are the consequence of it, so
-- numbering is derived from this value rather than from MAX(seat_number):
-- seat_number is VARCHAR, and a lexicographic maximum would order '9' above
-- '100', producing a duplicate at 101.
--
-- V1__initial_schema.sql is already deployed and is not touched. The unique
-- key it defines, uk_seat_library_number (library_id, seat_number), is what
-- guarantees generated numbers stay unique, so no new constraint is needed.
-- ============================================================

ALTER TABLE library
    ADD COLUMN seat_count INT NOT NULL DEFAULT 0 AFTER currency;

-- Existing libraries predate the field. They are given the standard starting
-- seat count rather than a per-library figure, so every library that already
-- exists is configured identically.
UPDATE library
SET seat_count = 100;

-- Materialise the seats that the seat count now promises.
--
-- NOT EXISTS makes this idempotent and collision-proof: a library that
-- already owns a seat with one of these numbers keeps the seat it has. The
-- seats seeded by V1 are lettered (A001, B001), so nothing is skipped in
-- practice - the guard is there for any library created between V1 and this
-- migration.
--
-- The explicit COLLATE is required, not decorative: CAST() yields the connection
-- collation (utf8mb4_0900_ai_ci on MySQL 8), while seat.seat_number is
-- utf8mb4_unicode_ci from V1, and comparing the two is error 1267.
--
-- created_at, updated_at and version all carry schema defaults, so they are
-- deliberately left to the database.
INSERT INTO seat (library_id, seat_number, status)
WITH RECURSIVE seat_numbers AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seat_numbers WHERE n < 100
)
SELECT l.library_id,
       CAST(sn.n AS CHAR) COLLATE utf8mb4_unicode_ci,
       'AVAILABLE'
FROM library l
CROSS JOIN seat_numbers sn
WHERE NOT EXISTS (
    SELECT 1
    FROM seat s
    WHERE s.library_id = l.library_id
      AND s.seat_number = CAST(sn.n AS CHAR) COLLATE utf8mb4_unicode_ci
)
ORDER BY l.library_id, sn.n;
