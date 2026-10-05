package com.Govlyx.AI.repository;

import com.Govlyx.AI.model.TransactionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionHistoryRepository extends JpaRepository<TransactionHistory, Long> {
    Optional<TransactionHistory> findByRazorpayOrderId(String razorpayOrderId);
    List<TransactionHistory> findByUserIdOrderByCreatedAtDesc(Long userId);
}
