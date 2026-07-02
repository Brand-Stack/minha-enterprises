package com.app.billing.dao;

import com.app.billing.model.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExpenseRepository extends MongoRepository<Expense, String> {
    List<Expense> findByCategory(String category);
    List<Expense> findByExpenseDateBetween(LocalDate startDate, LocalDate endDate);
    Page<Expense> findByExpenseDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);
    List<Expense> findByCategoryAndExpenseDateBetween(String category, LocalDate startDate, LocalDate endDate);
    Page<Expense> findByCategoryAndExpenseDateBetween(String category, LocalDate startDate, LocalDate endDate, Pageable pageable);
    List<Expense> findByPurchaseBillId(String purchaseBillId);
}

