package com.nexo.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardData(
        BigDecimal todayRevenue,
        long todaySales,
        long todayUnits,
        long lowStockProducts,
        BigDecimal monthRevenue,
        long monthSales,
        BigDecimal averageTicket,
        BigDecimal estimatedProfit,
        long financedSales,
        long productsInStock,
        BigDecimal inventoryCostValue,
        List<DailyRevenue> dailyRevenue,
        List<CategoryUnits> categoryUnits,
        List<PaymentTotal> paymentTotals) {

    public record DailyRevenue(LocalDate date, BigDecimal amount) {
    }

    public record CategoryUnits(String category, long units) {
    }

    public record PaymentTotal(String method, BigDecimal amount) {
    }
}
