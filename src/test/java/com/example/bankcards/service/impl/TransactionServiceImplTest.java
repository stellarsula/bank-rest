package com.example.bankcards.service.impl;

import com.example.bankcards.dto.TransactionRequest;
import com.example.bankcards.dto.TransactionResponse;
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
import com.example.bankcards.util.CardEncryptionUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionServiceImpl — юнит тесты")
class TransactionServiceImplTest {

    @Mock CardRepository cardRepository;
    @Mock UserRepository userRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock CardEncryptionUtil encryptionUtil;

    @InjectMocks TransactionServiceImpl transactionService;

    private User buildUser() {
        return User.builder()
                .id(1L)
                .username("john")
                .build();
    }

    private Card buildCard(Long id, BigDecimal balance, CardStatus status) {
        return Card.builder()
                .id(id)
                .cardNumberEncrypted("enc-" + id)
                .expiryDate(LocalDate.now().plusYears(2))
                .status(status)
                .balance(balance)
                .owner(buildUser())
                .build();
    }

    private TransactionRequest buildRequest(Long srcId, Long tgtId, BigDecimal amount) {
        TransactionRequest req = new TransactionRequest();
        req.setSourceCardId(srcId);
        req.setTargetCardId(tgtId);
        req.setAmount(amount);
        req.setDescription("Тест");
        return req;
    }

    private Transaction buildTransaction(Card src, Card tgt, BigDecimal amount) {
        return Transaction.builder()
                .id(100L)
                .sourceCard(src)
                .targetCard(tgt)
                .amount(amount)
                .description("Тест")
                .build();
    }

    @Test
    @DisplayName("transfer — успешный перевод, балансы обновляются")
    void transfer_success_balancesUpdated() {
        Card src = buildCard(1L, BigDecimal.valueOf(1000), CardStatus.ACTIVE);
        Card tgt = buildCard(2L, BigDecimal.valueOf(200), CardStatus.ACTIVE);
        TransactionRequest req = buildRequest(1L, 2L, BigDecimal.valueOf(300));

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(buildUser()));
        when(cardRepository.findByIdAndOwnerId(1L, 1L)).thenReturn(Optional.of(src));
        when(cardRepository.findByIdAndOwnerId(2L, 1L)).thenReturn(Optional.of(tgt));
        when(encryptionUtil.mask(any())).thenReturn("**** **** **** 0000");
        when(transactionRepository.save(any())).thenReturn(buildTransaction(src, tgt, BigDecimal.valueOf(300)));

        TransactionResponse response = transactionService.transfer(req, "john");

        assertThat(src.getBalance()).isEqualByComparingTo("700");
        assertThat(tgt.getBalance()).isEqualByComparingTo("500");
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getAmount()).isEqualByComparingTo("300");
        verify(cardRepository, times(2)).save(any(Card.class));
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    @DisplayName("transfer — та же карта бросает CardOperationException")
    void transfer_sameCard_throws() {
        TransactionRequest req = buildRequest(1L, 1L, BigDecimal.valueOf(100));
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(buildUser()));

        assertThatThrownBy(() -> transactionService.transfer(req, "john"))
                .isInstanceOf(CardOperationException.class)
                .hasMessageContaining("ту же карту");

        verify(cardRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("transfer — недостаточно средств бросает InsufficientFundsException")
    void transfer_insufficientFunds_throws() {
        Card src = buildCard(1L, BigDecimal.valueOf(50), CardStatus.ACTIVE);
        Card tgt = buildCard(2L, BigDecimal.ZERO, CardStatus.ACTIVE);
        TransactionRequest req = buildRequest(1L, 2L, BigDecimal.valueOf(500));

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(buildUser()));
        when(cardRepository.findByIdAndOwnerId(1L, 1L)).thenReturn(Optional.of(src));
        when(cardRepository.findByIdAndOwnerId(2L, 1L)).thenReturn(Optional.of(tgt));

        assertThatThrownBy(() -> transactionService.transfer(req, "john"))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Недостаточно");

        verify(cardRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("transfer — заблокированная карта-источник бросает CardOperationException")
    void transfer_blockedSource_throws() {
        Card src = buildCard(1L, BigDecimal.valueOf(1000), CardStatus.BLOCKED);
        Card tgt = buildCard(2L, BigDecimal.ZERO, CardStatus.ACTIVE);
        TransactionRequest req = buildRequest(1L, 2L, BigDecimal.valueOf(100));

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(buildUser()));
        when(cardRepository.findByIdAndOwnerId(1L, 1L)).thenReturn(Optional.of(src));
        when(cardRepository.findByIdAndOwnerId(2L, 1L)).thenReturn(Optional.of(tgt));

        assertThatThrownBy(() -> transactionService.transfer(req, "john"))
                .isInstanceOf(CardOperationException.class)
                .hasMessageContaining("неактивна");
    }

    @Test
    @DisplayName("transfer — заблокированная карта-получатель бросает CardOperationException")
    void transfer_blockedTarget_throws() {
        Card src = buildCard(1L, BigDecimal.valueOf(1000), CardStatus.ACTIVE);
        Card tgt = buildCard(2L, BigDecimal.ZERO, CardStatus.BLOCKED);
        TransactionRequest req = buildRequest(1L, 2L, BigDecimal.valueOf(100));

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(buildUser()));
        when(cardRepository.findByIdAndOwnerId(1L, 1L)).thenReturn(Optional.of(src));
        when(cardRepository.findByIdAndOwnerId(2L, 1L)).thenReturn(Optional.of(tgt));

        assertThatThrownBy(() -> transactionService.transfer(req, "john"))
                .isInstanceOf(CardOperationException.class)
                .hasMessageContaining("неактивна");
    }

    @Test
    @DisplayName("transfer — пользователь не найден бросает ResourceNotFoundException")
    void transfer_userNotFound_throws() {
        TransactionRequest req = buildRequest(1L, 2L, BigDecimal.valueOf(100));
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.transfer(req, "ghost"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("transfer — карта-источник не найдена бросает ResourceNotFoundException")
    void transfer_sourceCardNotFound_throws() {
        TransactionRequest req = buildRequest(99L, 2L, BigDecimal.valueOf(100));

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(buildUser()));
        when(cardRepository.findByIdAndOwnerId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.transfer(req, "john"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("источник");
    }

    @Test
    @DisplayName("transfer — перевод ровно всего баланса проходит успешно")
    void transfer_exactBalance_success() {
        Card src = buildCard(1L, BigDecimal.valueOf(300), CardStatus.ACTIVE);
        Card tgt = buildCard(2L, BigDecimal.ZERO, CardStatus.ACTIVE);
        TransactionRequest req = buildRequest(1L, 2L, BigDecimal.valueOf(300));

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(buildUser()));
        when(cardRepository.findByIdAndOwnerId(1L, 1L)).thenReturn(Optional.of(src));
        when(cardRepository.findByIdAndOwnerId(2L, 1L)).thenReturn(Optional.of(tgt));
        when(encryptionUtil.mask(any())).thenReturn("**** **** **** 0000");
        when(transactionRepository.save(any())).thenReturn(buildTransaction(src, tgt, BigDecimal.valueOf(300)));

        transactionService.transfer(req, "john");

        assertThat(src.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(tgt.getBalance()).isEqualByComparingTo("300");
    }
}