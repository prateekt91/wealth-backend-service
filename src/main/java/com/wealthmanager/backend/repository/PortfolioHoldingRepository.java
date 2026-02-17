package com.wealthmanager.backend.repository;

import com.wealthmanager.backend.model.PortfolioHolding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioHoldingRepository extends JpaRepository<PortfolioHolding, Long> {

    List<PortfolioHolding> findAllByOrderByInstrumentTypeAscSymbolAsc();

    List<PortfolioHolding> findByInstrumentTypeOrderBySymbolAsc(String instrumentType);

    Optional<PortfolioHolding> findByInstrumentTypeAndSymbol(String instrumentType, String symbol);
}
