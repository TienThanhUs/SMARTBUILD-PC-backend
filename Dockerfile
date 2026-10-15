# ==============================================================================
# STAGE 1: deps — Tải toàn bộ dependencies và plugins để tận dụng Docker layer cache
# ==============================================================================
FROM eclipse-temurin:21-jdk-alpine AS deps

WORKDIR /build

# Chỉ copy các file cấu hình dependency để tận dụng tối đa cache
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Sử dụng dummy source để kích hoạt trọn vẹn package lifecycle, kéo toàn bộ plugins & transitive deps
RUN mkdir -p src/main/java/dummy && \
    echo "package dummy; public class Dummy { public static void main(String[] args) {} }" > src/main/java/dummy/Dummy.java && \
    ./mvnw package -DskipTests -B && \
    rm -rf src/ target/

# ==============================================================================
# STAGE 2: build — Biên dịch mã nguồn và đóng gói file JAR ở chế độ offline
# ==============================================================================
FROM deps AS build

WORKDIR /build

# Copy mã nguồn ứng dụng
COPY src/ src/

# Build offline (-o) để đảm bảo toàn bộ thư viện đã có đủ từ stage deps
RUN ./mvnw -o package -DskipTests && \
    find target -maxdepth 1 -name "*.jar" ! -name "*.original" -exec cp {} /build/app.jar \;

# ==============================================================================
# STAGE 3: runtime — Image chạy ứng dụng tối giản, bảo mật, non-root user
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL org.opencontainers.image.title="SmartBuild PC Backend" \
      org.opencontainers.image.description="SmartBuild PC Spring Boot REST API Service"

# Tạo group và user hệ thống non-root với UID/GID 10001, khóa shell đăng nhập
RUN addgroup -g 10001 -S appgroup && \
    adduser -u 10001 -S appuser -G appgroup -s /sbin/nologin

WORKDIR /app

# Tạo thư mục tạm riêng biệt cấp quyền ghi cho appuser
RUN mkdir -p /app/tmp && \
    chown -R appuser:appgroup /app && \
    chmod 0700 /app/tmp

# Copy file jar từ stage build với quyền read-only (0444)
COPY --from=build --chown=appuser:appgroup /build/app.jar /app/app.jar
RUN chmod 0444 /app/app.jar && \
    chmod 0555 /app

USER appuser:appgroup

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-Djava.security.egd=file:/dev/./urandom", "-Djava.io.tmpdir=/app/tmp", "-jar", "app.jar"]
