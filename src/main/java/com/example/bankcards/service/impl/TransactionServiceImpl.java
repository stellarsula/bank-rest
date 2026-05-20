package com.example.bankcards.service.impl;

import com.example.bankcards.dto.PageResponse;
import com.example.bankcards.dto.TransactionResponse;
import com.example.bankcards.dto.TransactionRequest;
import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.CardStatus;
import com.example.bankcards.entity.Transaction;
import com.example.bankcards.entity.User;
import com.example.bankcards.exception.CardOperationException;
import com.example.bankcards.exception.InsufficientFundsException;
import com.example.bankcards.exception.ResourceNotFoundException;
import com.example.bankcards.repository.CardRepository;
import com.example.bankcards.repository.TransactionRepository;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.service.TransactionService;
import com.example.bankcards.util.CardEncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final CardEncryptionUtil encryptionUtil;

    @Override
    @Transactional
    public TransactionResponse transfer(TransactionRequest request, String username) {
        if (request.getSourceCardId().equals(request.getTargetCardId()))
            throw new CardOperationException("Нельзя перевести средства на ту же карту");

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));

        Card source = cardRepository.findByIdAndOwnerId(request.getSourceCardId(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Карта-источник не найдена или не ваша"));

        Card target = cardRepository.findByIdAndOwnerId(request.getTargetCardId(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Карта-получатель не найдена или не ваша"));

        if (source.getStatus() != CardStatus.ACTIVE)
            throw new CardOperationException("Карта-источник неактивна (статус: " + source.getStatus() + ")");
        if (target.getStatus() != CardStatus.ACTIVE)
            throw new CardOperationException("Карта-получатель неактивна (статус: " + target.getStatus() + ")");
        if (source.getBalance().compareTo(request.getAmount()) < 0)
            throw new InsufficientFundsException(
                    "Недостаточно средств. Доступно: " + source.getBalance() + ", требуется: " + request.getAmount());

        source.setBalance(source.getBalance().subtract(request.getAmount()));
        target.setBalance(target.getBalance().add(request.getAmount()));
        cardRepository.save(source);
        cardRepository.save(target);

        Transaction transaction = transactionRepository.save(Transaction.builder()
                .sourceCard(source).targetCard(target)
                .amount(request.getAmount()).description(request.getDescription())
                .build());

        return toResponse(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getMyTransactions(String username, Pageable pageable) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
        return PageResponse.from(transactionRepository.findByUserId(user.getId(), pageable).map(this::toResponse));
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .sourceCardId(transaction.getSourceCard().getId())
                .sourceCardMasked(encryptionUtil.mask(transaction.getSourceCard().getCardNumberEncrypted()))
                .targetCardId(transaction.getTargetCard().getId())
                .targetCardMasked(encryptionUtil.mask(transaction.getTargetCard().getCardNumberEncrypted()))
                .amount(transaction.getAmount())
                .description(transaction.getDescription())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}