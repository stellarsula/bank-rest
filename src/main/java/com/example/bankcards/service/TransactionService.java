package com.example.bankcards.service;

import com.example.bankcards.dto.PageResponse;
import com.example.bankcards.dto.TransactionResponse;
import com.example.bankcards.dto.TransactionRequest;
import org.springframework.data.domain.Pageable;

public interface TransactionService {
    TransactionResponse transfer(TransactionRequest request, String username);
    PageResponse<TransactionResponse> getMyTransactions(String username, Pageable pageable);
}