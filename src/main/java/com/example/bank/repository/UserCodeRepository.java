package com.example.bank.repository;

import com.example.bank.domain.UserCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface UserCodeRepository extends JpaRepository<UserCode, Long> {

    /** The newest code wins; older ones are simply ignored. Locked so guesses are counted one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserCode> findFirstByUserIdAndPurposeOrderByIdDesc(Long userId, UserCode.Purpose purpose);
}
