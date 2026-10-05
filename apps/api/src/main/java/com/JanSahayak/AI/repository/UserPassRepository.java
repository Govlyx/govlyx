package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.enums.UserPassStatus;
import com.JanSahayak.AI.model.UserPass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserPassRepository extends JpaRepository<UserPass, Long> {

    @Query("SELECT u FROM UserPass u WHERE u.userId = :userId AND u.status = 'ACTIVE' AND u.validUntil > CURRENT_TIMESTAMP ORDER BY u.validUntil DESC LIMIT 1")
    Optional<UserPass> findActivePassByUserId(@Param("userId") Long userId);

    List<UserPass> findByUserIdOrderByValidUntilDesc(Long userId);
    
    Optional<UserPass> findByRazorpayOrderId(String razorpayOrderId);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserPass u WHERE u.razorpayOrderId = :razorpayOrderId")
    Optional<UserPass> findByRazorpayOrderIdForUpdate(@Param("razorpayOrderId") String razorpayOrderId);
}
