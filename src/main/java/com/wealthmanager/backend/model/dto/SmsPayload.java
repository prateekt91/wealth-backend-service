package com.wealthmanager.backend.model.dto;

import jakarta.validation.constraints.NotBlank;

public record SmsPayload(

        @NotBlank(message = "Sender must not be blank")
        String sender,

        @NotBlank(message = "Body must not be blank")
        String body,

        String receivedAt,

        String deviceId
) {
}
