package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.TransactionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionHistoryRepository extends JpaRepository<TransactionHistory, Long> {
    Optional<TransactionHistory> findByRazorpayOrderId(String razorpayOrderId);
    List<TransactionHistory> findByUserIdOrderByCreatedAtDesc(Long userId);
}
