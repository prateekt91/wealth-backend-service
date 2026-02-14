package com.wealthmanager.backend.repository;

import com.wealthmanager.backend.model.RawIngestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RawIngestionRepository extends JpaRepository<RawIngestion, Long> {

    List<RawIngestion> findByProcessedFalse();

    boolean existsBySourceId(String sourceId);
}
