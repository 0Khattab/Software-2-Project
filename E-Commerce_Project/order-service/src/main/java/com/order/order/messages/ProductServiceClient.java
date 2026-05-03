package com.order.order.messages;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;

/**
 * HTTP client for Product Service internal endpoints.
 * Calls http://product-service:8083/api/internal/...
 * These routes are NOT exposed through the API Gateway.
 * Security comes from Docker network position, not JWT.
 */
@Component
@Slf4j
public class ProductServiceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ProductServiceClient(
            RestTemplate restTemplate,
            @Value("${services.product-service}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public ProductDetail getProductDetail(String productId) {
        String url = baseUrl + "products/" + productId;
        try {
            ProductDetail detail = restTemplate.getForObject(url, ProductDetail.class);
            if (detail == null) {
                throw new RuntimeException("Empty response from Product Service for productId=" + productId);
            }
            return detail;
        } catch (HttpClientErrorException.NotFound e) {
            throw new com.order.order.Exceptions.ResourceNotFoundException(
                    "Product not found: " + productId);
        } catch (com.order.order.Exceptions.ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Product Service unreachable for productId={}: {}", productId, e.getMessage());
            throw new com.order.order.Exceptions.ServiceUnavailableException(
                    "Product Service is currently unavailable. Please try again.");
        }
    }

    public void decrementStock(String productId, int qty) {
        String url = baseUrl + "products/" + productId
                + "/stock/decrement?qty=" + qty;
        try {
            restTemplate.put(url, null);
            log.debug("Stock decremented: productId={} qty={}", productId, qty);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                // Stock ran out between validation and decrement
                log.warn("STOCK_CONFLICT: productId={} qty={} - insufficient stock at decrement time",
                        productId, qty);
            } else {
                log.error("STOCK_DECREMENT_FAILED: productId={} qty={} status={}: {}",
                        productId, qty, e.getStatusCode(), e.getMessage());
            }
        } catch (Exception e) {
            // Non-fatal — log for reconciliation
            log.error("STOCK_DISCREPANCY: decrementStock failed productId={} qty={}: {}",
                    productId, qty, e.getMessage());
        }
    }

    // ── Inner DTO — mirrors Product Service internal response ─────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductDetail {
        private String productId;
        private String productName;
        private String primaryImageUrl;
        private BigDecimal price;
        private int stockQty;
        private String stockStatus;
        List<VariantResponseDTO> variants;
    //     Long id,
    //     String name,
    //     String description,
    //     BigDecimal price,
    //     ProductStatus status,
    //     String category,
    //     String categoryParent,
    //     List<String>images,
    }
    
    public record VariantResponseDTO(
            Long id,
            String size,
            Integer stockQuantity) {
    }
}