package com.nexo.model;

public record Sale(
        String id,
        String time,
        String customer,
        String total,
        String method,
        String status) {
}
