package com.order.order.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.order.order.interfaces.CartService;
import com.order.order.messages.*;
import com.order.order.messages.ProductServiceClient.ProductDetail;
import com.order.order.messages.ProductServiceClient.VariantResponseDTO;
import com.order.order.repos.*;
import com.order.order.DTOs.Request.*;
import com.order.order.DTOs.Response.*;
import com.order.order.Entites.Cart;
import com.order.order.Entites.CartItem;
import com.order.order.Exceptions.*;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductServiceClient productClient;

    @Value("${order.max-cart-items:50}")
    private int maxCartItems;

    // ── GET /api/cart ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(String userId) {
        Cart cart = findOrCreateCart(userId);
        return toCartResponse(cart);
    }

    // ── POST /api/cart/items ──────────────────────────────────────────────────

    @Override
    @Transactional
    public CartResponse addItem(String userId, AddCartItemRequest request) {
        Cart cart = findOrCreateCart(userId);

        if (cart.getItems().size() >= maxCartItems) {
            throw new IllegalStateException(
                    "Cart cannot exceed " + maxCartItems + " items.");
        }

        cart.getItems().stream()
                .filter(i -> i.getProductId().equals(request.getProductId())
                        && matchesVariant(i, request.getVariantId()))
                .findFirst()
                .ifPresentOrElse(
                        existing -> {
                            existing.setQuantity(existing.getQuantity() + request.getQuantity());
                            log.debug("Cart item quantity updated: productId={} variantId={} newQty={}",
                                    request.getProductId(), request.getVariantId(),
                                    existing.getQuantity());
                        },
                        () -> {
                            ProductDetail detail = productClient.getProductDetail(request.getProductId());

                            VariantResponseDTO variant = detail.getVariants().stream()
                                    .filter(v -> v.getId().equals(Long.parseLong(request.getVariantId())))
                                    .findFirst()
                                    .orElseThrow(() -> new ResourceNotFoundException(
                                            "Variant not found: " + request.getVariantId()
                                                    + " for product: " + request.getProductId()));
                            if (variant.getStockQuantity() == null
                                    || variant.getStockQuantity() <= 0) {
                                throw new InsufficientStockException(
                                        detail.getName() + " (size: " + variant.getSize() + ")",
                                        request.getQuantity(),
                                        0);
                            }
                            if (variant.getStockQuantity() < request.getQuantity()) {
                                throw new InsufficientStockException(
                                        detail.getName() + " (size: " + variant.getSize() + ")",
                                        request.getQuantity(),
                                        variant.getStockQuantity());
                            }
                            String ID = String.valueOf(detail.getId());

                            CartItem newItem = CartItem.builder()
                                    .cart(cart)
                                    .productId(ID)
                                    .productName(detail.getName())
                                    .imageUrl(detail.getImages().stream().findFirst().orElse(null))
                                    .unitPrice(detail.getPrice())
                                    .variantId(variant.getId()) 
                                    .variantLabel("Size: " + variant.getSize()) 
                                    .quantity(request.getQuantity())
                                    .build();

                            cart.getItems().add(newItem);
                            log.debug("New item added: productId={} variantId={} size={} price={}",
                                    ID , variant.getId(),
                                    variant.getSize(), detail.getPrice());
                        });

        cartRepository.save(cart);
        return toCartResponse(cart);
    }

    // ── PUT /api/cart/items/{id} ──────────────────────────────────────────────

    @Override
    @Transactional
    public CartResponse updateItem(String userId, String itemId,
            UpdateCartItemRequest request) {
        CartItem item = cartItemRepository
                .findByIdAndCartUserId(itemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cart item not found: " + itemId));

        item.setQuantity(request.getQuantity());
        cartItemRepository.save(item);

        Cart cart = findOrCreateCart(userId);
        return toCartResponse(cart);
    }

    // ── DELETE /api/cart/items/{id} ───────────────────────────────────────────

    @Override
    @Transactional
    public void removeItem(String userId, String itemId) {
        CartItem item = cartItemRepository
                .findByIdAndCartUserId(itemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cart item not found: " + itemId));

        Cart cart = item.getCart();
        cart.getItems().remove(item);
        cartRepository.save(cart);
    }

    // ── DELETE /api/cart ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public void clearCart(String userId) {
        cartRepository.findByUserId(userId).ifPresent(cart -> {
            cart.getItems().clear();
            cartRepository.save(cart);
            log.debug("Cart cleared for userId={}", userId);
        });
    }

    // ── GET /api/cart/count ───────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public int getCartCount(String userId) {
        return cartRepository.findByUserId(userId)
                .map(cart -> cart.getItems().stream()
                        .mapToInt(CartItem::getQuantity)
                        .sum())
                .orElse(0);
    }


    private Cart findOrCreateCart(String userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    log.debug("Creating new cart for userId={}", userId);
                    return cartRepository.save(Cart.builder().userId(userId).build());
                });
    }


    private boolean matchesVariant(CartItem item, String  requestedVariantId) {
        if (item.getVariantId() == null && requestedVariantId == null)
            return true;
        if (item.getVariantId() == null || requestedVariantId == null)
            return false;
        return item.getVariantId().equals(requestedVariantId);
    }

    private CartResponse toCartResponse(Cart cart) {
        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(item -> {
                    BigDecimal lineTotal = item.getUnitPrice()
                            .multiply(BigDecimal.valueOf(item.getQuantity()));
                    return CartItemResponse.builder()
                            .itemId(item.getId())
                            .productId(item.getProductId())
                            .productName(item.getProductName())
                            .variantId(item.getVariantId())
                            .variantLabel(item.getVariantLabel())
                            .imageUrl(item.getImageUrl())
                            .unitPrice(item.getUnitPrice())
                            .quantity(item.getQuantity())
                            .lineTotal(lineTotal)
                            .build();
                })
                .toList();

        BigDecimal subtotal = itemResponses.stream()
                .map(CartItemResponse::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalItems = cart.getItems().stream()
                .mapToInt(CartItem::getQuantity)
                .sum();

        return CartResponse.builder()
                .cartId(cart.getId())
                .items(itemResponses)
                .subtotal(subtotal)
                .totalItems(totalItems)
                .build();
    }
}