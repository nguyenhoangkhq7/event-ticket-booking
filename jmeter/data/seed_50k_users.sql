-- ============================================================================
-- SQL Script: Seed 50,000 Test Users & High-Capacity Concert Inventory
-- For JMeter Load & Stress Testing
-- ============================================================================

-- 1. Create 50,000 Test Users
-- Password for all accounts is 'password123' hashed with BCrypt ($2a$12$H/tnE.97GdULmDOTk4MRO.y6rRv/4f30bKA4IV2RESH2b9nX49wQu)
-- This runs in ~1-2 seconds in PostgreSQL using generate_series.

INSERT INTO users (email, password_hash, fullname, role, status, created_at, updated_at)
SELECT 
    'perf_user_' || i || '@perf.com',
    '$2a$12$H/tnE.97GdULmDOTk4MRO.y6rRv/4f30bKA4IV2RESH2b9nX49wQu',
    'Performance User ' || i,
    'CUSTOMER',
    'ACTIVE',
    NOW(),
    NOW()
FROM generate_series(1, 50000) AS i
ON CONFLICT (email) DO NOTHING;

-- 2. Ensure High-Capacity Concert & Inventory for Performance Testing
-- Add a dedicated Mega-Concert for load testing (Concert ID will be generated or referenced)
INSERT INTO concerts (name, description, venue, start_at, end_at, sale_start_at, sale_end_at, status)
VALUES (
    'Mega Load Test Music Festival 2026',
    'Concert dedicated for 50,000 concurrent user performance and stress testing.',
    'National Stadium - Grand Arena',
    NOW() + INTERVAL '30 days',
    NOW() + INTERVAL '30 days 6 hours',
    NOW() - INTERVAL '5 days',
    NOW() + INTERVAL '25 days',
    'PUBLISHED'
) ON CONFLICT DO NOTHING;

-- 3. Update Existing Ticket Inventory with High Volume for Stress Testing
-- Ensure Category 1, 2, 3, 4 have plenty of stock so tests don't run out prematurely
UPDATE ticket_inventory 
SET total_quantity = 500000, 
    reserved_quantity = 0, 
    sold_quantity = 0 
WHERE ticket_category_id IN (1, 2, 3, 4);

-- 4. Insert Performance Test High-Volume Vouchers
INSERT INTO vouchers (name, code, discount_type, discount_value, max_redemptions, redeemed_count, max_per_user, starts_at, ends_at, status)
VALUES 
    ('Perf Test 10% Off', 'PERF10', 'PERCENTAGE', 10.00, 100000, 0, 10, NOW() - INTERVAL '1 day', NOW() + INTERVAL '30 days', 'ACTIVE'),
    ('Perf Test 50k Off', 'PERF50K', 'FIXED', 50000.00, 100000, 0, 10, NOW() - INTERVAL '1 day', NOW() + INTERVAL '30 days', 'ACTIVE')
ON CONFLICT (code) DO UPDATE 
SET max_redemptions = 100000, max_per_user = 10, status = 'ACTIVE';

-- 5. Verification query
SELECT COUNT(*) AS total_perf_users FROM users WHERE email LIKE '%@perf.com';
