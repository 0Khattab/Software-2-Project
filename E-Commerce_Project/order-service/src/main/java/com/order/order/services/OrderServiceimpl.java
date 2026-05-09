package com.order.order.services;

import com.order.order.Entites.Cart;
import com.order.order.Entites.CartItem;
import com.order.order.Entites.Order;
import com.order.order.Entites.OrderAddress;
import com.order.order.Entites.OrderItem;
import com.order.order.Events.OrderPlacedEvent;
import com.order.order.Events.Publishers.OrderEventPublisher;
import com.order.order.Exceptions.CartEmptyException;
import com.order.order.Exceptions.InsufficientStockException;
import com.order.order.Exceptions.ResourceNotFoundException;
import com.order.order.interfaces.OrderService;
import com.order.order.messages.PaymentServiceClient;
import com.order.order.messages.ProductServiceClient;
import com.order.order.messages.ProductServiceClient.VariantResponseDTO;
import com.order.order.repos.CartRepository;
import com.order.order.repos.OrderRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.order.order.DTOs.Request.PlaceOrderRequest;
import com.order.order.DTOs.Request.UpdateOrderStatusRequest;
import com.order.order.DTOs.Response.AddressResponse;
import com.order.order.DTOs.Response.AdminStatsResponse;
import com.order.order.DTOs.Response.OrderDetailResponse;
import com.order.order.DTOs.Response.OrderItemResponse;
import com.order.order.DTOs.Response.OrderSummaryResponse;
import com.order.order.DTOs.Response.PagedResponse;
import com.order.order.ENUMs.OrderStatus;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceimpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductServiceClient productClient;
    private final PaymentServiceClient paymentClient;
    private final OrderEventPublisher eventProducer;

    @Override
    @Transactional
    public OrderDetailResponse placeOrder(String userId, PlaceOrderRequest request) {

        Cart cart = cartRepository.findByUserId(userId)
                .filter(c -> !c.getItems().isEmpty())
                .orElseThrow(CartEmptyException::new);

        List<VariantResponseDTO> liveVariants = new ArrayList<>();

        for (CartItem cartItem : cart.getItems()) {
            ProductServiceClient.ProductDetail live = productClient.getProductDetail(cartItem.getProductId());
            log.info("Product: {}", live);
            VariantResponseDTO liveVariant = live.getVariants().stream()
                    .filter(v -> v.getId().equals(cartItem.getVariantId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Variant not found: " + cartItem.getVariantId()));

            if (liveVariant.getStockQuantity() < cartItem.getQuantity()) {
                throw new InsufficientStockException(
                        cartItem.getProductName() + " - " + cartItem.getVariantLabel(),
                        cartItem.getQuantity(),
                        liveVariant.getStockQuantity());
            }

            liveVariants.add(liveVariant);
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        List<CartItem> cartItemList = new ArrayList<>(cart.getItems());

        for (int i = 0; i < cartItemList.size(); i++) {
            CartItem ci = cartItemList.get(i);
            ProductServiceClient.ProductDetail live = productClient.getProductDetail(ci.getProductId());
            VariantResponseDTO lv = liveVariants.get(i);

            BigDecimal livePrice = live.getPrice();
            BigDecimal lineTotal = livePrice.multiply(BigDecimal.valueOf(ci.getQuantity()));
            subtotal = subtotal.add(lineTotal);

            orderItems.add(OrderItem.builder()
                    .productId(ci.getProductId())
                    .productName(ci.getProductName())
                    .imageUrl(ci.getImageUrl())
                    .unitPrice(livePrice)
                    .quantity(ci.getQuantity())
                    .lineTotal(lineTotal)
                    .variantId(ci.getVariantId())
                    .variantLabel(ci.getVariantLabel())
                    .build());
        }

        var addr = request.getShippingAddress();
        OrderAddress shippingAddress = OrderAddress.builder()
                .recipientName(addr.getRecipientName())
                .phone(addr.getPhone())
                .street(addr.getStreet())
                .city(addr.getCity())
                .country(addr.getCountry())
                .zipCode(addr.getZipCode())
                .build();

        BigDecimal shippingFee = calculateShippingFee(subtotal);
        BigDecimal totalAmount = subtotal.add(shippingFee);

        Order order = Order.builder()
                .userId(userId)
                .status(OrderStatus.PENDING)
                .paymentMethod(request.getPaymentMethod())
                .subtotal(subtotal)
                .shippingFee(shippingFee)
                .totalAmount(totalAmount)
                .notes(request.getNotes())
                .build();

        orderItems.forEach(item -> item.setOrder(order));
        order.getItems().addAll(orderItems);
        shippingAddress.setOrder(order);
        order.setShippingAddress(shippingAddress);

        Order saved = orderRepository.save(order);
        log.info("Order {} created for userId={} total={}", saved.getId(), userId, totalAmount);

        // for (CartItem ci : cart.getItems()) {
        // productClient.decrementStock(
        // ci.getProductId(),
        // ci.getQuantity(),
        // ci.getVariantId()
        // );
        // }

        cart.getItems().clear();
        cartRepository.save(cart);

        eventProducer.publishOrderPlaced(buildOrderPlacedEvent(saved));

        return toDetailResponse(saved, null, null);
    }

    @Override
    @Transactional
    public PagedResponse<OrderSummaryResponse> getMyOrders(
            String userId, int page, int size) {
        Page<Order> result = orderRepository
                .findByUserIdOrderByPlacedAtDesc(userId, PageRequest.of(page, size));
        return toPagedSummary(result);
    }

    @Override
    @Transactional
    public OrderDetailResponse getMyOrderDetail(String userId, String orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found: " + orderId));

        PaymentServiceClient.PaymentStatus payment = paymentClient.getPaymentStatus(orderId);

        return toDetailResponse(order, payment.getStatus(), payment.getPaidAt());
    }

    @Override
    @Transactional
    public PagedResponse<OrderSummaryResponse> getAllOrders(
            OrderStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<Order> result = (status != null)
                ? orderRepository.findByStatusOrderByPlacedAtDesc(status, pageable)
                : orderRepository.findAllByOrderByPlacedAtDesc(pageable);
        return toPagedSummary(result);
    }

    @Override
    @Transactional
    public OrderDetailResponse getAnyOrderDetail(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found: " + orderId));
        PaymentServiceClient.PaymentStatus payment = paymentClient.getPaymentStatus(orderId);
        return toDetailResponse(order, payment.getStatus(), payment.getPaidAt());
    }

    @Override
    @Transactional
    public void updateOrderStatus(String orderId, UpdateOrderStatusRequest req) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found: " + orderId));
        order.setStatus(req.getStatus());
        orderRepository.save(order);
        log.info("Order {} status updated to {} by admin", orderId, req.getStatus());
    }

    @Override
    @Transactional
    public AdminStatsResponse getStats() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        return AdminStatsResponse.builder()
                .totalOrders(orderRepository.count())
                .ordersToday(orderRepository.countBetween(startOfDay, endOfDay))
                .totalRevenue(orderRepository.totalRevenue())
                .revenueToday(orderRepository.revenueBetween(startOfDay, endOfDay))
                .pendingOrders(orderRepository.countByStatus(OrderStatus.PENDING))
                .cancelledOrders(orderRepository.countByStatus(OrderStatus.CANCELLED))
                .build();
    }

    @Override
    @Transactional
    public void confirmOrderAfterPayment(String orderId) {
        orderRepository.findById(orderId).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.CONFIRMED);
                orderRepository.save(order);
                log.info("Order {} auto-confirmed after payment", orderId);
            }
        });
    }

    private BigDecimal calculateShippingFee(BigDecimal subtotal) {
        return subtotal.compareTo(BigDecimal.valueOf(100)) >= 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(5.00);
    }

    private OrderPlacedEvent buildOrderPlacedEvent(Order order) {
        List<OrderPlacedEvent.OrderItemEvent> itemEvents = order.getItems().stream()
                .map(i -> OrderPlacedEvent.OrderItemEvent.builder()
                        .productId(i.getProductId())
                        .productName(i.getProductName())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .build())
                .toList();

        return OrderPlacedEvent.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .items(itemEvents)
                .totalAmount(order.getTotalAmount())
                .recipientName(order.getShippingAddress().getRecipientName())
                .shippingCity(order.getShippingAddress().getCity())
                .shippingCountry(order.getShippingAddress().getCountry())
                .paymentMethod(order.getPaymentMethod().name())
                .placedAt(order.getPlacedAt())
                .build();
    }

    private OrderDetailResponse toDetailResponse(Order order,
            String paymentStatus,
            LocalDateTime paidAt) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(i -> OrderItemResponse.builder()
                        .itemId(i.getId())
                        .productId(i.getProductId())
                        .productName(i.getProductName())
                        .imageUrl(i.getImageUrl())
                        .unitPrice(i.getUnitPrice())
                        .quantity(i.getQuantity())
                        .lineTotal(i.getLineTotal())
                        .build())
                .toList();

        AddressResponse address = null;
        if (order.getShippingAddress() != null) {
            OrderAddress a = order.getShippingAddress();
            address = AddressResponse.builder()
                    .recipientName(a.getRecipientName())
                    .phone(a.getPhone())
                    .street(a.getStreet())
                    .city(a.getCity())
                    .country(a.getCountry())
                    .zipCode(a.getZipCode())
                    .build();
        }

        return OrderDetailResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .subtotal(order.getSubtotal())
                .shippingFee(order.getShippingFee())
                .totalAmount(order.getTotalAmount())
                .notes(order.getNotes())
                .items(items)
                .shippingAddress(address)
                .placedAt(order.getPlacedAt())
                .updatedAt(order.getUpdatedAt())
                .paymentStatus(paymentStatus)
                .paidAt(paidAt)
                .build();
    }

    private PagedResponse<OrderSummaryResponse> toPagedSummary(Page<Order> page) {
        List<OrderSummaryResponse> items = page.getContent().stream()
                .map(order -> {
                    String firstName = order.getItems().isEmpty() ? ""
                            : order.getItems().get(0).getProductName();
                    String firstImage = order.getItems().isEmpty() ? null
                            : order.getItems().get(0).getImageUrl();
                    return OrderSummaryResponse.builder()
                            .orderId(order.getId())
                            .status(order.getStatus())
                            .totalAmount(order.getTotalAmount())
                            .itemCount(order.getItems().size())
                            .placedAt(order.getPlacedAt())
                            .firstItemName(firstName)
                            .firstItemImage(firstImage)
                            .build();
                })
                .toList();

        return PagedResponse.<OrderSummaryResponse>builder()
                .items(items)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }
}
