package com.smartbuildpc.backend.common.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordByteLengthValidatorTest {

    private PasswordByteLengthValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PasswordByteLengthValidator();
    }

    @Test
    @DisplayName("72 bytes ASCII hợp lệ, 73 bytes ASCII bị từ chối")
    void given_asciiPasswords_whenValidate_thenCheckExactByteBoundary() {
        String exactly72Bytes = "a".repeat(72);
        assertThat(exactly72Bytes.getBytes(StandardCharsets.UTF_8).length).isEqualTo(72);
        assertThat(validator.isValid(exactly72Bytes, null)).isTrue();

        String exactly73Bytes = "a".repeat(73);
        assertThat(exactly73Bytes.getBytes(StandardCharsets.UTF_8).length).isEqualTo(73);
        assertThat(validator.isValid(exactly73Bytes, null)).isFalse();
    }

    @Test
    @DisplayName("Mật khẩu tiếng Việt có dấu tính đúng theo số byte UTF-8")
    void given_vietnamesePasswords_whenValidate_thenCalculateUtf8Bytes() {
        // Ký tự 'ệ' chiếm 3 bytes trong UTF-8
        // 24 ký tự 'ệ' = 72 bytes UTF-8 (độ dài ký tự = 24 >= 8) -> hợp lệ
        String vietnamese72Bytes = "ệ".repeat(24);
        assertThat(vietnamese72Bytes.length()).isEqualTo(24);
        assertThat(vietnamese72Bytes.getBytes(StandardCharsets.UTF_8).length).isEqualTo(72);
        assertThat(validator.isValid(vietnamese72Bytes, null)).isTrue();

        // 24 ký tự 'ệ' + 1 ký tự 'a' (1 byte) = 73 bytes UTF-8 -> bị từ chối
        String vietnamese73Bytes = "ệ".repeat(24) + "a";
        assertThat(vietnamese73Bytes.getBytes(StandardCharsets.UTF_8).length).isEqualTo(73);
        assertThat(validator.isValid(vietnamese73Bytes, null)).isFalse();
    }

    @Test
    @DisplayName("Mật khẩu dưới 8 ký tự bị từ chối, đúng 8 ký tự hợp lệ")
    void given_shortPasswords_whenValidate_thenRejectUnder8Chars() {
        String sevenChars = "1234567";
        assertThat(validator.isValid(sevenChars, null)).isFalse();

        String eightChars = "12345678";
        assertThat(validator.isValid(eightChars, null)).isTrue();
    }

    @Test
    @DisplayName("Null mật khẩu bị từ chối")
    void given_nullPassword_whenValidate_thenReturnsFalse() {
        assertThat(validator.isValid(null, null)).isFalse();
    }
}
