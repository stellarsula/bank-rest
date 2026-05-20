package com.example.bankcards.repository;

import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.CardStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, Long>, JpaSpecificationExecutor<Card> {

    @Query("SELECT c FROM Card c WHERE c.owner.id = :ownerId AND (:status IS NULL OR c.status = :status)")
    Page<Card> findByOwnerIdAndOptionalStatus(@Param("ownerId") Long ownerId,
                                              @Param("status") CardStatus status,
                                              Pageable pageable);

    Optional<Card> findByIdAndOwnerId(Long id, Long ownerId);

    boolean existsByCardNumberEncrypted(String cardNumberEncrypted);
}