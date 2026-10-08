package com.example.bank.repository;

import com.example.bank.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByOwnerIdOrderByIdAsc(Long ownerId);

    Page<Account> findAllByOrderByIdAsc(Pageable pageable);

    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * SELECT ... FOR UPDATE: the row stays locked until the current transaction
     * ends, so other transactions that want the same lock wait their turn. Every
     * operation that reads a balance and then acts on it must load the account
     * through this method.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(Long id);

    @Query(value = "SELECT nextval('account_number_seq')", nativeQuery = true)
    long nextAccountNumber();
}
