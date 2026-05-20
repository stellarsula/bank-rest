package com.example.bankcards.service.impl;

import com.example.bankcards.dto.CardResponse;
import com.example.bankcards.dto.CreateCardRequest;
import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.CardStatus;
import com.example.bankcards.entity.User;
import com.example.bankcards.exception.CardOperationException;
import com.example.bankcards.exception.ResourceNotFoundException;
import com.example.bankcards.repository.CardRepository;
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
@DisplayName("CardServiceImpl — юнит тесты")
class CardServiceImplTest {

    @Mock CardRepository cardRepository;
    @Mock UserRepository userRepository;
    @Mock CardEncryptionUtil encryptionUtil;

    @InjectMocks CardServiceImpl cardService;

    private User buildUser() {
        return User.builder()
                .id(1L)
                .username("john")
                .email("john@test.com")
                .build();
    }

    private Card buildCard(Long id, CardStatus status) {
        return Card.builder()
                .id(id)
                .cardNumberEncrypted("enc-123")
                .cardHolderName("JOHN DOE")
                .expiryDate(LocalDate.now().plusYears(3))
                .status(status)
                .balance(BigDecimal.valueOf(1000))
                .owner(buildUser())
                .build();
    }

    private void mockMask() {
        when(encryptionUtil.mask(any())).thenReturn("**** **** **** 1234");
    }

    @Test
    @DisplayName("createCard — создаёт карту с начальным балансом")
    void createCard_withBalance_success() {
        CreateCardRequest req = new CreateCardRequest();
        req.setOwnerId(1L);
        req.setCardHolderName("John Doe");
        req.setExpiryDate(LocalDate.now().plusYears(3));
        req.setInitialBalance(BigDecimal.valueOf(500));

        when(userRepository.findById(1L)).thenReturn(Optional.of(buildUser()));
        when(encryptionUtil.encrypt(any())).thenReturn("enc-123");
        when(cardRepository.existsByCardNumberEncrypted(any())).thenReturn(false);
        mockMask();
        when(cardRepository.save(any())).thenAnswer(inv -> {
            Card c = inv.getArgument(0);
            c.setId(10L);
            return c;
        });

        CardResponse response = cardService.createCard(req);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getCardHolderName()).isEqualTo("JOHN DOE");
        assertThat(response.getStatus()).isEqualTo(CardStatus.ACTIVE);
        assertThat(response.getBalance()).isEqualByComparingTo("500");
        verify(cardRepository).save(any(Card.class));
    }

    @Test
    @DisplayName("createCard — без баланса устанавливается 0")
    void createCard_withoutBalance_defaultsZero() {
        CreateCardRequest req = new CreateCardRequest();
        req.setOwnerId(1L);
        req.setCardHolderName("John Doe");
        req.setExpiryDate(LocalDate.now().plusYears(3));
        req.setInitialBalance(null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(buildUser()));
        when(encryptionUtil.encrypt(any())).thenReturn("enc-123");
        when(cardRepository.existsByCardNumberEncrypted(any())).thenReturn(false);
        mockMask();
        when(cardRepository.save(any())).thenAnswer(inv -> {
            Card c = inv.getArgument(0);
            c.setId(10L);
            return c;
        });

        CardResponse response = cardService.createCard(req);

        assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("createCard — пользователь не найден бросает ResourceNotFoundException")
    void createCard_userNotFound_throws() {
        CreateCardRequest req = new CreateCardRequest();
        req.setOwnerId(999L);
        req.setCardHolderName("Ghost");
        req.setExpiryDate(LocalDate.now().plusYears(1));

        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.createCard(req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");

        verify(cardRepository, never()).save(any());
    }

    @Test
    @DisplayName("blockCard — активная карта успешно блокируется")
    void blockCard_active_success() {
        Card card = buildCard(10L, CardStatus.ACTIVE);
        when(cardRepository.findById(10L)).thenReturn(Optional.of(card));
        when(cardRepository.save(card)).thenReturn(card);
        mockMask();

        CardResponse response = cardService.blockCard(10L);

        assertThat(response.getStatus()).isEqualTo(CardStatus.BLOCKED);
        verify(cardRepository).save(card);
    }

    @Test
    @DisplayName("blockCard — уже заблокированная карта бросает CardOperationException")
    void blockCard_alreadyBlocked_throws() {
        Card card = buildCard(10L, CardStatus.BLOCKED);
        when(cardRepository.findById(10L)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> cardService.blockCard(10L))
                .isInstanceOf(CardOperationException.class)
                .hasMessageContaining("уже заблокирована");

        verify(cardRepository, never()).save(any());
    }

    @Test
    @DisplayName("blockCard — истёкшую карту нельзя заблокировать")
    void blockCard_expired_throws() {
        Card card = buildCard(10L, CardStatus.EXPIRED);
        when(cardRepository.findById(10L)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> cardService.blockCard(10L))
                .isInstanceOf(CardOperationException.class)
                .hasMessageContaining("истёкшую");

        verify(cardRepository, never()).save(any());
    }

    @Test
    @DisplayName("blockCard — карта не найдена бросает ResourceNotFoundException")
    void blockCard_notFound_throws() {
        when(cardRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.blockCard(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("activateCard — заблокированная карта успешно активируется")
    void activateCard_blocked_success() {
        Card card = buildCard(10L, CardStatus.BLOCKED);
        when(cardRepository.findById(10L)).thenReturn(Optional.of(card));
        when(cardRepository.save(card)).thenReturn(card);
        mockMask();

        CardResponse response = cardService.activateCard(10L);

        assertThat(response.getStatus()).isEqualTo(CardStatus.ACTIVE);
        verify(cardRepository).save(card);
    }

    @Test
    @DisplayName("activateCard — уже активная карта бросает CardOperationException")
    void activateCard_alreadyActive_throws() {
        Card card = buildCard(10L, CardStatus.ACTIVE);
        when(cardRepository.findById(10L)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> cardService.activateCard(10L))
                .isInstanceOf(CardOperationException.class)
                .hasMessageContaining("уже активна");
    }

    @Test
    @DisplayName("activateCard — истёкшую карту нельзя активировать")
    void activateCard_expired_throws() {
        Card card = buildCard(10L, CardStatus.EXPIRED);
        when(cardRepository.findById(10L)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> cardService.activateCard(10L))
                .isInstanceOf(CardOperationException.class)
                .hasMessageContaining("истёкшую");
    }

    @Test
    @DisplayName("deleteCard — успешное удаление")
    void deleteCard_success() {
        Card card = buildCard(10L, CardStatus.ACTIVE);
        when(cardRepository.findById(10L)).thenReturn(Optional.of(card));

        cardService.deleteCard(10L);

        verify(cardRepository).delete(card);
    }

    @Test
    @DisplayName("deleteCard — карта не найдена бросает ResourceNotFoundException")
    void deleteCard_notFound_throws() {
        when(cardRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.deleteCard(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(cardRepository, never()).delete((Card) any());
    }

    @Test
    @DisplayName("getMyCard — возвращает карту владельца")
    void getMyCard_success() {
        User user = buildUser();
        Card card = buildCard(10L, CardStatus.ACTIVE);

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(cardRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(card));
        mockMask();

        CardResponse response = cardService.getMyCard(10L, "john");

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getOwnerUsername()).isEqualTo("john");
    }

    @Test
    @DisplayName("getMyCard — чужая карта бросает ResourceNotFoundException")
    void getMyCard_notOwner_throws() {
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(buildUser()));
        when(cardRepository.findByIdAndOwnerId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.getMyCard(99L, "john"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── requestBlockCard ────────────────────────────────────────────────────

    @Test
    @DisplayName("requestBlockCard — пользователь блокирует свою активную карту")
    void requestBlockCard_success() {
        User user = buildUser();
        Card card = buildCard(10L, CardStatus.ACTIVE);

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(cardRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(card));
        when(cardRepository.save(card)).thenReturn(card);
        mockMask();

        CardResponse response = cardService.requestBlockCard(10L, "john");

        assertThat(response.getStatus()).isEqualTo(CardStatus.BLOCKED);
    }

    @Test
    @DisplayName("requestBlockCard — нельзя заблокировать уже заблокированную")
    void requestBlockCard_alreadyBlocked_throws() {
        User user = buildUser();
        Card card = buildCard(10L, CardStatus.BLOCKED);

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(cardRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> cardService.requestBlockCard(10L, "john"))
                .isInstanceOf(CardOperationException.class)
                .hasMessageContaining("активную");
    }

    @Test
    @DisplayName("requestBlockCard — чужая карта бросает ResourceNotFoundException")
    void requestBlockCard_notOwner_throws() {
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(buildUser()));
        when(cardRepository.findByIdAndOwnerId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.requestBlockCard(99L, "john"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}