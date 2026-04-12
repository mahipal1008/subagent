package com.subscrub.repository;

import com.subscrub.model.QueryLineage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QueryLineageRepository extends JpaRepository<QueryLineage, Long> {

    List<QueryLineage> findByUserIdOrderByCreatedAtDesc(String userId);
}
