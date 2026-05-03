package com.order.order.repos;

import com.order.order.Entites.Order;
import com.order.order.ENUMs.OrderStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, String> {

    // User's own orders
    Page<Order> findByUserIdOrderByPlacedAtDesc(String userId, Pageable pageable);

    Optional<Order> findByIdAndUserId(String id, String userId);

    // Admin queries
    Page<Order> findAllByOrderByPlacedAtDesc(Pageable pageable);

    Page<Order> findByStatusOrderByPlacedAtDesc(OrderStatus status, Pageable pageable);

    long countByStatus(OrderStatus status);

    // Admin dashboard stats
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status NOT IN ('CANCELLED', 'REFUNDED')")
    long countActiveOrders();

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status IN ('CONFIRMED','PROCESSING','SHIPPED','DELIVERED')")
    BigDecimal totalRevenue();

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status IN ('CONFIRMED','PROCESSING','SHIPPED','DELIVERED') AND o.placedAt BETWEEN :from AND :to")
    BigDecimal revenueBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.placedAt BETWEEN :from AND :to")
    long countBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
