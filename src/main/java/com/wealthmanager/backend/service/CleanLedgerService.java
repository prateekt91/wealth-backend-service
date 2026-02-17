package com.wealthmanager.backend.service;

import com.wealthmanager.backend.model.CleanLedgerEntry;
import com.wealthmanager.backend.model.dto.CleanLedgerEntryResponse;
import com.wealthmanager.backend.repository.CleanLedgerEntryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
public class CleanLedgerService {

    private final CleanLedgerEntryRepository cleanLedgerEntryRepository;

    public CleanLedgerService(CleanLedgerEntryRepository cleanLedgerEntryRepository) {
        this.cleanLedgerEntryRepository = cleanLedgerEntryRepository;
    }

    public Page<CleanLedgerEntryResponse> getAllEntries(Pageable pageable) {
        return cleanLedgerEntryRepository.findAllByOrderByLedgerDateDesc(pageable)
                .map(this::toResponse);
    }

    public List<CleanLedgerEntryResponse> getEntriesByInstrumentType(String instrumentType, Pageable pageable) {
        return cleanLedgerEntryRepository.findByInstrumentTypeOrderByLedgerDateDesc(instrumentType, pageable)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<CleanLedgerEntryResponse> getEntriesByRawIngestionId(Long rawIngestionId) {
        return cleanLedgerEntryRepository.findByRawIngestionId(rawIngestionId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CleanLedgerEntryResponse saveEntry(CleanLedgerEntry entry) {
        CleanLedgerEntry saved = cleanLedgerEntryRepository.save(entry);
        return toResponse(saved);
    }

    private CleanLedgerEntryResponse toResponse(CleanLedgerEntry e) {
        return new CleanLedgerEntryResponse(
                e.getId(),
                e.getRawIngestionId(),
                e.getEntryType(),
                e.getInstrumentType(),
                e.getSymbol(),
                e.getName(),
                e.getQuantity(),
                e.getPrice(),
                e.getAmount(),
                e.getCurrency(),
                e.getLedgerDate(),
                e.getDescription(),
                e.getCreatedAt()
        );
    }
}
