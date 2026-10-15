# SMARTBUILD-PC Backend

Hệ thống Backend cho nền tảng thương mại điện tử và tư vấn linh kiện máy tính SmartBuild PC (Spring Boot 3.3.x, Java 21 LTS, PostgreSQL 16).

---

## 1. Yêu cầu môi trường
- Java 21 LTS
- Maven 3.9+ (hoặc Maven Wrapper `./mvnw` đi kèm)
- Docker & Docker Compose

Nếu máy chạy macOS có nhiều phiên bản Java, thiết lập biến môi trường trước khi chạy Maven:
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

---

## 2. Cấu hình biến môi trường
Tạo file `.env` từ `.env.example` nếu chạy trực tiếp trên máy host:
```bash
cp .env.example .env
```

---

## 3. Chạy kiểm thử tự động (Unit & Integration Tests)
Chạy bộ kiểm thử (sử dụng Testcontainers PostgreSQL 16 tự động khởi tạo):
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw clean test
```

---

## 4. Hướng dẫn Chạy Ứng Dụng

### Cách 1: Chạy toàn bộ với Docker Compose (Khuyên dùng)
Khởi động đồng thời cả Backend và cơ sở dữ liệu PostgreSQL 16:
```bash
docker compose up --build -d
```
- **Tài liệu API Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON Spec:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Xem log hệ thống:** `docker compose logs -f backend`
- **Dừng hệ thống:** `docker compose down`

---

### Cách 2: Chạy phục vụ Phát triển cục bộ (Local Dev)
1. Khởi động cơ sở dữ liệu PostgreSQL trong nền:
```bash
docker compose up -d postgres
```
2. Khởi chạy ứng dụng Spring Boot:
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw spring-boot:run
```