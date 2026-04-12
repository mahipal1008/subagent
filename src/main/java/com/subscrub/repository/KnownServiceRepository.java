package com.subscrub.repository;

import com.subscrub.model.KnownService;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface KnownServiceRepository extends JpaRepository<KnownService, Integer> {

    Optional<KnownService> findByMerchantPattern(String merchantPattern);

    List<KnownService> findByCategory(String category);

    List<KnownService> findByIsActiveTrue();
}
