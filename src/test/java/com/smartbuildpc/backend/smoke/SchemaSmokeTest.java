package com.smartbuildpc.backend.smoke;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class SchemaSmokeTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("App khởi động thành công và Flyway tạo đủ 11 bảng cốt lõi")
    void flyway_shouldCreateAll11Tables() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                String.class
        );

        List<String> expectedTables = List.of(
                "users",
                "addresses",
                "categories",
                "products",
                "carts",
                "cart_items",
                "coupons",
                "orders",
                "order_items",
                "payment_transactions",
                "reviews"
        );

        assertThat(tables).containsAll(expectedTables);
    }

    @Test
    @DisplayName("Ràng buộc UNIQUE trên users.email hoạt động chính xác")
    void users_uniqueEmailConstraint_shouldPreventDuplicates() {
        jdbcTemplate.update(
                "INSERT INTO users (full_name, email, password_hash, role, is_active) VALUES (?, ?, ?, ?, ?)",
                "Nguyễn Văn A", "smoke_test_unique@example.com", "$2a$10$hashedpassword", "CUSTOMER", true
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO users (full_name, email, password_hash, role, is_active) VALUES (?, ?, ?, ?, ?)",
                "Nguyễn Văn B", "smoke_test_unique@example.com", "$2a$10$hashedpassword2", "CUSTOMER", true
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("GIN index trên cột products.specs tồn tại và đúng cấu hình")
    void products_specsGinIndex_shouldExist() {
        List<String> indexDefs = jdbcTemplate.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'products' AND indexname = 'idx_products_specs_gin'",
                String.class
        );

        assertThat(indexDefs).hasSize(1);
        assertThat(indexDefs.getFirst().toLowerCase()).contains("using gin (specs)");
    }

    @Test
    @DisplayName("Ràng buộc CHECK (price >= 0) trên products chặn được giá trị âm")
    void products_checkPriceNonNegative_shouldRejectNegativePrice() {
        // Tạo category phụ thuộc nếu chưa có
        jdbcTemplate.update(
                "INSERT INTO categories (code, name, description) VALUES (?, ?, ?) ON CONFLICT (code) DO NOTHING",
                "SMOKE_CPU", "Smoke Test CPU", "Test Category"
        );

        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE code = 'SMOKE_CPU'",
                Long.class
        );

        assertThat(categoryId).isNotNull();

        // Kiểm tra giá âm bị chặn
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO products (category_id, name, brand, price, stock_quantity, status, specs) " +
                        "VALUES (?, ?, ?, ?, ?, ?, '{}'::jsonb)",
                categoryId, "Negative Price CPU", "TestBrand", -50000.0, 10, "ACTIVE"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
