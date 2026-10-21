package com.nexo.model;

import java.time.LocalDateTime;

public record MovementRecord(
        long id,
        String type,
        String product,
        int quantityChange,
        LocalDateTime dateTime,
        String note,
        String user) {
}
