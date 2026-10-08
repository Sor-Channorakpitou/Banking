package com.example.bank.repository;

import com.example.bank.domain.Payee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayeeRepository extends JpaRepository<Payee, Long> {

    List<Payee> findByOwnerIdOrderByNicknameAsc(Long ownerId);

    Optional<Payee> findByIdAndOwnerId(Long id, Long ownerId);

    boolean existsByOwnerIdAndAccountNumber(Long ownerId, String accountNumber);
}
