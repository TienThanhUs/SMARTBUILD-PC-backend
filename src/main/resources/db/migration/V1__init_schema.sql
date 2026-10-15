-- =============================================================================
-- SMARTBUILD PC - V1__init_schema.sql
-- Khởi tạo toàn bộ lược đồ 11 bảng cốt lõi của hệ thống
-- =============================================================================

-- 1. BẢNG USERS (Người dùng hệ thống)
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    role VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);

-- 2. BẢNG ADDRESSES (Sổ địa chỉ người dùng)
CREATE TABLE addresses (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    recipient_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    street VARCHAR(255) NOT NULL,
    ward VARCHAR(100),
    district VARCHAR(100),
    city VARCHAR(100) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_addresses_user_id ON addresses(user_id);

-- 3. BẢNG CATEGORIES (Danh mục linh kiện)
CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    CONSTRAINT uk_categories_code UNIQUE (code)
);

-- 4. BẢNG PRODUCTS (Sản phẩm linh kiện máy tính)
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    brand VARCHAR(100),
    price NUMERIC(15, 2) NOT NULL,
    stock_quantity INT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    image_url VARCHAR(500),
    description TEXT,
    color VARCHAR(50),
    specs JSONB NOT NULL DEFAULT '{}'::jsonb,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ,
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT chk_products_price CHECK (price >= 0),
    CONSTRAINT chk_products_stock_quantity CHECK (stock_quantity >= 0)
);

CREATE INDEX idx_products_cat_price_status ON products (category_id, price, status);
CREATE INDEX idx_products_specs_gin ON products USING gin (specs);

-- 5. BẢNG CARTS (Giỏ hàng của người dùng)
CREATE TABLE carts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_carts_user_id UNIQUE (user_id),
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 6. BẢNG CART_ITEMS (Chi tiết linh kiện trong giỏ)
CREATE TABLE cart_items (
    id BIGSERIAL PRIMARY KEY,
    cart_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    CONSTRAINT uk_cart_items_cart_product UNIQUE (cart_id, product_id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    CONSTRAINT chk_cart_items_quantity CHECK (quantity > 0)
);

-- 7. BẢNG COUPONS (Mã giảm giá khuyến mãi)
CREATE TABLE coupons (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    discount_type VARCHAR(30) NOT NULL,
    discount_value NUMERIC(15, 2) NOT NULL,
    min_order_value NUMERIC(15, 2) NOT NULL DEFAULT 0,
    max_discount_amount NUMERIC(15, 2),
    usage_limit INT NOT NULL,
    used_count INT NOT NULL DEFAULT 0,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_coupons_code UNIQUE (code),
    CONSTRAINT chk_coupons_used_count CHECK (used_count <= usage_limit)
);

-- 8. BẢNG ORDERS (Đơn hàng)
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    address_id BIGINT,
    shipping_address_snapshot JSONB NOT NULL,
    coupon_id BIGINT,
    total_amount NUMERIC(15, 2) NOT NULL,
    discount_amount NUMERIC(15, 2) NOT NULL DEFAULT 0,
    final_price NUMERIC(15, 2) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    payment_status VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ,
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_orders_address FOREIGN KEY (address_id) REFERENCES addresses(id) ON DELETE SET NULL,
    CONSTRAINT fk_orders_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id) ON DELETE SET NULL
);

CREATE INDEX idx_orders_user_status ON orders (user_id, status);
CREATE INDEX idx_orders_created_status ON orders (created_at, status);

-- 9. BẢNG ORDER_ITEMS (Chi tiết sản phẩm trong đơn hàng)
CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name_snapshot VARCHAR(255) NOT NULL,
    unit_price NUMERIC(15, 2) NOT NULL,
    quantity INT NOT NULL,
    subtotal NUMERIC(15, 2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT chk_order_items_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_product_id ON order_items (product_id);

-- 10. BẢNG PAYMENT_TRANSACTIONS (Giao dịch thanh toán)
CREATE TABLE payment_transactions (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    transaction_ref VARCHAR(100) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    payment_url VARCHAR(500),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payment_tx_ref UNIQUE (transaction_ref),
    CONSTRAINT fk_payment_tx_order FOREIGN KEY (order_id) REFERENCES orders(id)
);

CREATE INDEX idx_payment_tx_order_id ON payment_transactions (order_id);

-- 11. BẢNG REVIEWS (Đánh giá và nhận xét sản phẩm)
CREATE TABLE reviews (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    rating INT NOT NULL,
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reviews_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_reviews_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT uk_reviews_user_product_order UNIQUE (user_id, product_id, order_id),
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5)
);
