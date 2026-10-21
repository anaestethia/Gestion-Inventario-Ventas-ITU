package com.nexo.model;

import java.math.BigDecimal;

public record ProductImportRow(
        int sourceRow,
        String description,
        String size,
        String color,
        String brand,
        int quantity,
        int minimumStock,
        String season,
        BigDecimal purchaseCost,
        BigDecimal cashPrice,
        BigDecimal financedPrice,
        int installments,
        String barcode,
        String category) {
}
