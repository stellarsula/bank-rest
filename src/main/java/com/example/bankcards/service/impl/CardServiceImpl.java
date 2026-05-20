package com.example.bankcards.service.impl;

import com.example.bankcards.dto.CardResponse;
import com.example.bankcards.dto.CreateCardRequest;
import com.example.bankcards.dto.PageResponse;
import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.CardStatus;
import com.example.bankcards.entity.User;
import com.example.bankcards.exception.CardOperationException;
import com.example.bankcards.exception.ResourceNotFoundException;
import com.example.bankcards.repository.CardRepository;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.service.CardService;
import com.example.bankcards.util.CardEncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardServiceImpl implements CardService {

    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final CardEncryptionUtil encryptionUtil;

    @Override
    @Transactional
    public CardResponse createCard(CreateCardRequest request) {
        User owner = userRepository.findById(request.getOwnerId())
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден: id=" + request.getOwnerId()));

        String number = generateUniqueCardNumber();
        Card card = Card.builder()
                .cardNumberEncrypted(encryptionUtil.encrypt(number))
                .cardHolderName(request.getCardHolderName().toUpperCase())
                .expiryDate(request.getExpiryDate())
                .balance(request.getInitialBalance() != null ? request.getInitialBalance() : BigDecimal.ZERO)
                .owner(owner)
                .status(CardStatus.ACTIVE)
                .build();

        return toResponse(cardRepository.save(card));
    }

    @Override
    @Transactional
    public CardResponse blockCard(Long cardId) {
        Card card = findOrThrow(cardId);
        if (card.getStatus() == CardStatus.BLOCKED)
            throw new CardOperationException("Карта уже заблокирована");
        if (card.getStatus() == CardStatus.EXPIRED)
            throw new CardOperationException("Нельзя заблокировать истёкшую карту");
        card.setStatus(CardStatus.BLOCKED);
        return toResponse(cardRepository.save(card));
    }

    @Override
    @Transactional
    public CardResponse activateCard(Long cardId) {
        Card card = findOrThrow(cardId);
        if (card.getStatus() == CardStatus.ACTIVE)
            throw new CardOperationException("Карта уже активна");
        if (card.getStatus() == CardStatus.EXPIRED)
            throw new CardOperationException("Нельзя активировать истёкшую карту");
        card.setStatus(CardStatus.ACTIVE);
        return toResponse(cardRepository.save(card));
    }

    @Override
    @Transactional
    public void deleteCard(Long cardId) {
        cardRepository.delete(findOrThrow(cardId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CardResponse> getAllCards(CardStatus status, Pageable pageable) {
        Page<Card> page = (status != null)
                ? cardRepository.findAll((root, q, cb) -> cb.equal(root.get("status"), status), pageable)
                : cardRepository.findAll(pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public CardResponse getCardById(Long cardId) {
        return toResponse(findOrThrow(cardId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CardResponse> getMyCards(String username, CardStatus status, Pageable pageable) {
        User user = findUserOrThrow(username);
        return PageResponse.from(
                cardRepository.findByOwnerIdAndOptionalStatus(user.getId(), status, pageable)
                              .map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public CardResponse getMyCard(Long cardId, String username) {
        User user = findUserOrThrow(username);
        return toResponse(cardRepository.findByIdAndOwnerId(cardId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Карта не найдена: id=" + cardId)));
    }

    @Override
    @Transactional
    public CardResponse requestBlockCard(Long cardId, String username) {
        User user = findUserOrThrow(username);
        Card card = cardRepository.findByIdAndOwnerId(cardId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Карта не найдена: id=" + cardId));
        if (card.getStatus() != CardStatus.ACTIVE)
            throw new CardOperationException("Заблокировать можно только активную карту");
        card.setStatus(CardStatus.BLOCKED);
        return toResponse(cardRepository.save(card));
    }

    private Card findOrThrow(Long id) {
        return cardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Карта не найдена: id=" + id));
    }

    private User findUserOrThrow(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден: " + username));
    }

    private String generateUniqueCardNumber() {
        Random rng = new Random();
        String number;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) sb.append(rng.nextInt(10));
            number = sb.toString();
        } while (cardRepository.existsByCardNumberEncrypted(encryptionUtil.encrypt(number)));
        return number;
    }

    public CardResponse toResponse(Card card) {
        return CardResponse.builder()
                .id(card.getId())
                .maskedCardNumber(encryptionUtil.mask(card.getCardNumberEncrypted()))
                .cardHolderName(card.getCardHolderName())
                .expiryDate(card.getExpiryDate())
                .status(card.getStatus())
                .balance(card.getBalance())
                .ownerId(card.getOwner().getId())
                .ownerUsername(card.getOwner().getUsername())
                .createdAt(card.getCreatedAt())
                .build();
    }
}