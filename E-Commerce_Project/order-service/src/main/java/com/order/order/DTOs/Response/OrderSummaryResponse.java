package com.order.order.DTOs.Response;

import com.order.order.ENUMs.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSummaryResponse {
    private String orderId;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private int itemCount;
    private LocalDateTime placedAt;
    private String firstItemName;
    private String firstItemImage;
}