-- Sample Users
-- Password for all accounts is 'password123' hashed with BCrypt
INSERT INTO users (email, password_hash, fullname, role, status) VALUES
('admin@eventticket.com', '$2a$12$H/tnE.97GdULmDOTk4MRO.y6rRv/4f30bKA4IV2RESH2b9nX49wQu', 'System Admin', 'ADMIN', 'ACTIVE'),
('customer1@example.com', '$2a$12$H/tnE.97GdULmDOTk4MRO.y6rRv/4f30bKA4IV2RESH2b9nX49wQu', 'Nguyen Van A', 'CUSTOMER', 'ACTIVE'),
('customer2@example.com', '$2a$12$H/tnE.97GdULmDOTk4MRO.y6rRv/4f30bKA4IV2RESH2b9nX49wQu', 'Tran Thi B', 'CUSTOMER', 'ACTIVE');

-- Sample Concerts
INSERT INTO concerts (name, description, venue, start_at, end_at, sale_start_at, sale_end_at, status) VALUES
(
    'Anh Trai Vượt Ngàn Chông Gai 2026 - Live Concert',
    'Đêm nhạc hội bùng nổ của dàn Anh Trai với hàng loạt bản hit đình đám và sân khấu quy mô khủng.',
    'Sân vận động Quốc gia Mỹ Đình, Hà Nội',
    NOW() + INTERVAL '30 days',
    NOW() + INTERVAL '30 days 4 hours',
    NOW() - INTERVAL '5 days',
    NOW() + INTERVAL '25 days',
    'PUBLISHED'
),
(
    'Born Pink World Tour 2026',
    'Chuyến lưu diễn thế giới quy tụ hàng chục ngàn khán giả với hiệu ứng âm thanh ánh sáng chuẩn quốc tế.',
    'Sân vận động Quân khu 7, TP. Hồ Chí Minh',
    NOW() + INTERVAL '45 days',
    NOW() + INTERVAL '45 days 3 hours',
    NOW() - INTERVAL '2 days',
    NOW() + INTERVAL '40 days',
    'PUBLISHED'
),
(
    'Show Của Đen 2026',
    'Live concert cá nhân tiếp theo của Đen Vâu mang đến không gian âm nhạc mộc mạc và chân thật.',
    'Trung tâm Hội chợ và Triển lãm Sài Gòn (SECC), TP. Hồ Chí Minh',
    NOW() + INTERVAL '60 days',
    NOW() + INTERVAL '60 days 4 hours',
    NOW() - INTERVAL '1 day',
    NOW() + INTERVAL '55 days',
    'PUBLISHED'
);

-- Sample Ticket Categories
-- Concert 1 categories
INSERT INTO ticket_categories (concert_id, name, price, max_per_booking, status) VALUES
(1, 'VIP SVIP', 3500000.00, 4, 'ACTIVE'),
(1, 'CAT 1', 2200000.00, 4, 'ACTIVE'),
(1, 'CAT 2', 1200000.00, 6, 'ACTIVE'),
(1, 'Standard', 600000.00, 6, 'ACTIVE');

-- Concert 2 categories
INSERT INTO ticket_categories (concert_id, name, price, max_per_booking, status) VALUES
(2, 'VIP Standing', 4500000.00, 2, 'ACTIVE'),
(2, 'Seated CAT 1', 2800000.00, 4, 'ACTIVE'),
(2, 'Seated CAT 2', 1500000.00, 4, 'ACTIVE');

-- Concert 3 categories
INSERT INTO ticket_categories (concert_id, name, price, max_per_booking, status) VALUES
(3, 'Đồng Âm VIP', 2000000.00, 4, 'ACTIVE'),
(3, 'GA Standing', 900000.00, 6, 'ACTIVE');

-- Sample Ticket Inventory
-- Concert 1 inventory (categories 1 to 4)
INSERT INTO ticket_inventory (ticket_category_id, total_quantity, reserved_quantity, sold_quantity) VALUES
(1, 500, 0, 50),
(2, 1500, 0, 100),
(3, 3000, 0, 200),
(4, 5000, 0, 500);

-- Concert 2 inventory (categories 5 to 7)
INSERT INTO ticket_inventory (ticket_category_id, total_quantity, reserved_quantity, sold_quantity) VALUES
(5, 800, 0, 120),
(6, 2000, 0, 300),
(7, 4000, 0, 450);

-- Concert 3 inventory (categories 8 to 9)
INSERT INTO ticket_inventory (ticket_category_id, total_quantity, reserved_quantity, sold_quantity) VALUES
(8, 1000, 0, 80),
(9, 3500, 0, 250);

-- Sample Vouchers
INSERT INTO vouchers (name, code, discount_type, discount_value, max_redemptions, redeemed_count, max_per_user, starts_at, ends_at, status) VALUES
('Giảm 10% Khuyến Mãi Hè', 'SUMMER2026', 'PERCENTAGE', 10.00, 100, 5, 1, NOW() - INTERVAL '1 day', NOW() + INTERVAL '30 days', 'ACTIVE'),
('Giảm 50.000đ Đơn Đầu Tiên', 'WELCOME50', 'FIXED', 50000.00, 500, 12, 1, NOW() - INTERVAL '1 day', NOW() + INTERVAL '60 days', 'ACTIVE'),
('Giảm 20% Vé VIP Flashsale', 'VIPFLASH20', 'PERCENTAGE', 20.00, 50, 0, 1, NOW() - INTERVAL '1 day', NOW() + INTERVAL '14 days', 'ACTIVE');
