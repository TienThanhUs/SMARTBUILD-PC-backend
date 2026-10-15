package com.smartbuildpc.backend.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class PasswordByteLengthValidator implements ConstraintValidator<ValidPassword, String> {

    public static final int MIN_CHAR_LENGTH = 8;
    public static final int MAX_BYTE_LENGTH = 72;

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return false;
        }

        if (password.length() < MIN_CHAR_LENGTH) {
            setCustomMessage(context, "Mật khẩu phải có ít nhất " + MIN_CHAR_LENGTH + " ký tự");
            return false;
        }

        byte[] utf8Bytes = password.getBytes(StandardCharsets.UTF_8);
        if (utf8Bytes.length > MAX_BYTE_LENGTH) {
            setCustomMessage(context, "Mật khẩu không được vượt quá " + MAX_BYTE_LENGTH + " byte UTF-8");
            return false;
        }

        return true;
    }

    private void setCustomMessage(ConstraintValidatorContext context, String message) {
        if (context == null) {
            return;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}
