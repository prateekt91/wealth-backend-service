package com.wealthmanager.backend.repository;

import com.wealthmanager.backend.model.CleanLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CleanLedgerEntryRepository extends JpaRepository<CleanLedgerEntry, Long> {

    Page<CleanLedgerEntry> findAllByOrderByLedgerDateDesc(Pageable pageable);

    List<CleanLedgerEntry> findByInstrumentTypeOrderByLedgerDateDesc(String instrumentType, Pageable pageable);

    List<CleanLedgerEntry> findByRawIngestionId(Long rawIngestionId);
}
