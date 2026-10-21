package com.nexo.model;

import java.math.BigDecimal;

public record SaleLine(InventoryProduct product, int quantity, BigDecimal unitPrice) {
    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
