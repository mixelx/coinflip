package com.example.controller;

import com.example.exception.BadRequestException;
import io.micronaut.http.HttpRequest;
import jakarta.inject.Singleton;

@Singleton
public class TelegramUserHelper {

    private static final String HEADER_TG_USER_ID = "X-Tg-UserId";

    public long requireTelegramUserId(HttpRequest<?> request) {
        String headerValue = request.getHeaders().get(HEADER_TG_USER_ID);

        if (headerValue == null || headerValue.isBlank()) {
            throw new BadRequestException("Missing required header: " + HEADER_TG_USER_ID);
        }

        try {
            return Long.parseLong(headerValue.trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid " + HEADER_TG_USER_ID + " header value: " + headerValue);
        }
    }
}

