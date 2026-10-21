package com.nexo.model;

public record Product(
        String id,
        String barcode,
        String name,
        String category,
        String size,
        String color,
        int quantity,
        String cost,
        String cashPrice,
        String financedPrice,
        String installments) {
}
