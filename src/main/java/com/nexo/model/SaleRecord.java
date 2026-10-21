package com.nexo.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SaleRecord(
        int id,
        LocalDate date,
        String customer,
        String paymentMethod,
        String status,
        BigDecimal total,
        String seller) {
}
