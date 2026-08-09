package com.balancetrail.repository;

import com.balancetrail.entity.LedgerTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerTransactionRepository
    extends JpaRepository<LedgerTransactionEntity, String> {}
