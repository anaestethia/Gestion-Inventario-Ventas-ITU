package com.nexo.model;

public record StockMovement(
        String type,
        String id,
        String product,
        String quantity,
        String date,
        String user) {
}
