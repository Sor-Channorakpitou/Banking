package com.example.bank.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/** Someone the owner sends money to, saved under a nickname ("Mom", "Landlord"). */
@Entity
@Table(name = "payees")
public class Payee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "account_number", nullable = false, length = 34, updatable = false)
    private String accountNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Payee() {
    }

    public Payee(Long ownerId, String nickname, String accountNumber) {
        this.ownerId = ownerId;
        this.nickname = nickname;
        this.accountNumber = accountNumber;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
