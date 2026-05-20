package com.example.bankcards.service;

import com.example.bankcards.dto.CardResponse;
import com.example.bankcards.dto.CreateCardRequest;
import com.example.bankcards.dto.PageResponse;
import com.example.bankcards.entity.CardStatus;
import org.springframework.data.domain.Pageable;

public interface CardService {
    CardResponse createCard(CreateCardRequest request);
    CardResponse blockCard(Long cardId);
    CardResponse activateCard(Long cardId);
    void deleteCard(Long cardId);
    PageResponse<CardResponse> getAllCards(CardStatus status, Pageable pageable);
    CardResponse getCardById(Long cardId);
    PageResponse<CardResponse> getMyCards(String username, CardStatus status, Pageable pageable);
    CardResponse getMyCard(Long cardId, String username);
    CardResponse requestBlockCard(Long cardId, String username);
}