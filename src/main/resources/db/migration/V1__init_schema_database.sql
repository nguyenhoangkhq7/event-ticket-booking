CREATE TABLE users (
                       id              BIGSERIAL PRIMARY KEY,
                       email           VARCHAR(255) NOT NULL,
                       password_hash   VARCHAR(255) NOT NULL,
                       fullname        VARCHAR(255) NOT NULL,

                       role            VARCHAR(20) NOT NULL DEFAULT 'USER',
                       status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

                       created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                       CONSTRAINT uq_users_email
                           UNIQUE (email),

                       CONSTRAINT chk_users_role
                           CHECK (role IN ('USER', 'ADMIN')),

                       CONSTRAINT chk_users_status
                           CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE TABLE concerts (
                          id              BIGSERIAL PRIMARY KEY,

                          name            VARCHAR(255) NOT NULL,
                          description     TEXT,

                          venue           VARCHAR(255) NOT NULL,

                          start_at        TIMESTAMPTZ NOT NULL,
                          end_at          TIMESTAMPTZ,

                          sale_start_at   TIMESTAMPTZ NOT NULL,
                          sale_end_at     TIMESTAMPTZ NOT NULL,

                          status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

                          created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          CONSTRAINT chk_concerts_status
                              CHECK (
                                  status IN (
                                             'DRAFT',
                                             'PUBLISHED',
                                             'CANCELLED',
                                             'ENDED'
                                      )
                                  ),

                          CONSTRAINT chk_concerts_time
                              CHECK (end_at IS NULL OR end_at > start_at),

                          CONSTRAINT chk_concerts_sale_period
                              CHECK (sale_end_at > sale_start_at)
);

CREATE TABLE ticket_categories (
                                   id                  BIGSERIAL PRIMARY KEY,

                                   concert_id          BIGINT NOT NULL,
                                   name                VARCHAR(100) NOT NULL,

                                   price               NUMERIC(12, 2) NOT NULL,
                                   max_per_booking     INTEGER NOT NULL DEFAULT 4,

                                   status              VARCHAR(20) NOT NULL DEFAULT 'INACTIVE',

                                   created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                   CONSTRAINT fk_ticket_categories_concert
                                       FOREIGN KEY (concert_id)
                                           REFERENCES concerts(id),

                                   CONSTRAINT uq_ticket_categories_concert_name
                                       UNIQUE (concert_id, name),

                                   CONSTRAINT chk_ticket_categories_price
                                       CHECK (price >= 0),

                                   CONSTRAINT chk_ticket_categories_max_per_booking
                                       CHECK (max_per_booking > 0),

                                   CONSTRAINT chk_ticket_categories_status
                                       CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE ticket_inventory (
                                  ticket_category_id BIGINT PRIMARY KEY,

                                  total_quantity     INTEGER NOT NULL,
                                  reserved_quantity  INTEGER NOT NULL DEFAULT 0,
                                  sold_quantity      INTEGER NOT NULL DEFAULT 0,

                                  version            BIGINT NOT NULL DEFAULT 0,

                                  updated_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                  CONSTRAINT fk_ticket_inventory_category
                                      FOREIGN KEY (ticket_category_id)
                                          REFERENCES ticket_categories(id),

                                  CONSTRAINT chk_ticket_inventory_total
                                      CHECK (total_quantity >= 0),

                                  CONSTRAINT chk_ticket_inventory_reserved
                                      CHECK (reserved_quantity >= 0),

                                  CONSTRAINT chk_ticket_inventory_sold
                                      CHECK (sold_quantity >= 0),

                                  CONSTRAINT chk_ticket_inventory_capacity
                                      CHECK (
                                          reserved_quantity + sold_quantity <= total_quantity
                                          )
);

CREATE TABLE vouchers (
                          id                  BIGSERIAL PRIMARY KEY,

                          name                VARCHAR(255) NOT NULL,
                          code                VARCHAR(100) NOT NULL,

                          discount_type       VARCHAR(20) NOT NULL,
                          discount_value      NUMERIC(12, 2) NOT NULL,

                          max_redemptions     INTEGER NOT NULL,
                          redeemed_count      INTEGER NOT NULL DEFAULT 0,

                          max_per_user        INTEGER NOT NULL DEFAULT 1,

                          starts_at           TIMESTAMPTZ NOT NULL,
                          ends_at             TIMESTAMPTZ NOT NULL,

                          status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

                          created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          CONSTRAINT uq_vouchers_code
                              UNIQUE (code),

                          CONSTRAINT chk_vouchers_discount_type
                              CHECK (discount_type IN ('PERCENTAGE', 'FIXED')),

                          CONSTRAINT chk_vouchers_discount_value
                              CHECK (discount_value >= 0),

                          CONSTRAINT chk_vouchers_percentage
                              CHECK (
                                  discount_type <> 'PERCENTAGE'
                                      OR discount_value <= 100
                                  ),

                          CONSTRAINT chk_vouchers_max_redemptions
                              CHECK (max_redemptions > 0),

                          CONSTRAINT chk_vouchers_redeemed_count
                              CHECK (
                                  redeemed_count >= 0
                                      AND redeemed_count <= max_redemptions
                                  ),

                          CONSTRAINT chk_vouchers_max_per_user
                              CHECK (max_per_user > 0),

                          CONSTRAINT chk_vouchers_period
                              CHECK (ends_at > starts_at),

                          CONSTRAINT chk_vouchers_status
                              CHECK (
                                  status IN (
                                             'DRAFT',
                                             'ACTIVE',
                                             'USED_UP',
                                             'EXPIRED',
                                             'DISABLED'
                                      )
                                  )
);

CREATE TABLE bookings (
                          id                  BIGSERIAL PRIMARY KEY,

                          booking_code        VARCHAR(50) NOT NULL,

                          user_id             BIGINT NOT NULL,

                          status              VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',

                          subtotal            NUMERIC(12, 2) NOT NULL,
                          discount_amount     NUMERIC(12, 2) NOT NULL DEFAULT 0,
                          total_amount        NUMERIC(12, 2) NOT NULL,

                          voucher_id          BIGINT,

                          risk_status         VARCHAR(30) NOT NULL DEFAULT 'NORMAL',

    -- Reservation/payment expiration time
                          expires_at          TIMESTAMPTZ,

    -- Prevent duplicate booking caused by request retries
                          idempotency_key     VARCHAR(100) NOT NULL,

                          created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          CONSTRAINT fk_bookings_user
                              FOREIGN KEY (user_id)
                                  REFERENCES users(id),

                          CONSTRAINT fk_bookings_voucher
                              FOREIGN KEY (voucher_id)
                                  REFERENCES vouchers(id),

                          CONSTRAINT uq_bookings_code
                              UNIQUE (booking_code),

    -- Idempotency per user
                          CONSTRAINT uq_bookings_user_idempotency
                              UNIQUE (user_id, idempotency_key),

                          CONSTRAINT chk_bookings_status
                              CHECK (
                                  status IN (
                                             'RECEIVED',
                                             'PENDING_PAYMENT',
                                             'PAID',
                                             'EXPIRED',
                                             'CANCELLED',
                                             'FAILED'
                                      )
                                  ),

                          CONSTRAINT chk_bookings_amounts
                              CHECK (
                                  subtotal >= 0
                                      AND discount_amount >= 0
                                      AND total_amount >= 0
                                      AND discount_amount <= subtotal
                                      AND total_amount = subtotal - discount_amount
                                  ),

                          CONSTRAINT chk_bookings_risk_status
                              CHECK (
                                  risk_status IN (
                                                  'NORMAL',
                                                  'SUSPICIOUS',
                                                  'BLOCKED'
                                      )
                                  )
);

CREATE TABLE booking_items (
                               id                  BIGSERIAL PRIMARY KEY,

                               booking_id          BIGINT NOT NULL,
                               ticket_category_id  BIGINT NOT NULL,

                               quantity            INTEGER NOT NULL,
                               unit_price          NUMERIC(12, 2) NOT NULL,

                               subtotal            NUMERIC(14, 2)
                                   GENERATED ALWAYS AS (quantity * unit_price) STORED,

                               CONSTRAINT fk_booking_items_booking
                                   FOREIGN KEY (booking_id)
                                       REFERENCES bookings(id)
                                       ON DELETE CASCADE,

                               CONSTRAINT fk_booking_items_ticket_category
                                   FOREIGN KEY (ticket_category_id)
                                       REFERENCES ticket_categories(id),

                               CONSTRAINT uq_booking_items_booking_category
                                   UNIQUE (booking_id, ticket_category_id),

                               CONSTRAINT chk_booking_items_quantity
                                   CHECK (quantity > 0),

                               CONSTRAINT chk_booking_items_unit_price
                                   CHECK (unit_price >= 0)
);

CREATE TABLE voucher_redemptions (
                                     id                  BIGSERIAL PRIMARY KEY,

                                     voucher_id          BIGINT NOT NULL,
                                     user_id             BIGINT NOT NULL,
                                     booking_id          BIGINT NOT NULL,

                                     discount_amount     NUMERIC(12, 2) NOT NULL,

                                     created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                     CONSTRAINT fk_voucher_redemptions_voucher
                                         FOREIGN KEY (voucher_id)
                                             REFERENCES vouchers(id),

                                     CONSTRAINT fk_voucher_redemptions_user
                                         FOREIGN KEY (user_id)
                                             REFERENCES users(id),

                                     CONSTRAINT fk_voucher_redemptions_booking
                                         FOREIGN KEY (booking_id)
                                             REFERENCES bookings(id)
                                             ON DELETE CASCADE,

    -- One voucher redemption record per booking
                                     CONSTRAINT uq_voucher_redemptions_booking
                                         UNIQUE (booking_id),

                                     CONSTRAINT chk_voucher_redemptions_discount
                                         CHECK (discount_amount >= 0)
);


-- ============================================================
-- 11. INDEXES
-- ============================================================

-- Concert browsing
CREATE INDEX idx_concerts_status_sale_period
    ON concerts(status, sale_start_at, sale_end_at);

-- Ticket categories by concert
CREATE INDEX idx_ticket_categories_concert
    ON ticket_categories(concert_id);

-- Booking queries for operation dashboard
CREATE INDEX idx_bookings_status_created_at
    ON bookings(status, created_at DESC);

CREATE INDEX idx_bookings_user_created_at
    ON bookings(user_id, created_at DESC);

CREATE INDEX idx_bookings_expires_at
    ON bookings(expires_at)
    WHERE status IN ('RECEIVED', 'PENDING_PAYMENT');

-- Booking items
CREATE INDEX idx_booking_items_ticket_category
    ON booking_items(ticket_category_id);

-- Voucher lookup
CREATE INDEX idx_vouchers_status
    ON vouchers(status);

-- Voucher redemption monitoring
CREATE INDEX idx_voucher_redemptions_user
    ON voucher_redemptions(user_id);

CREATE UNIQUE INDEX uq_voucher_redemptions_voucher_user
    ON voucher_redemptions(voucher_id, user_id);