package com.useranalyzer.backend.repository;

import com.useranalyzer.backend.entity.PageConvertRate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PageConvertRateRepository extends JpaRepository<PageConvertRate, Long> {

    @Cacheable("pageConvertRates")
    List<PageConvertRate> findByTaskIdOrderByPageFlowAsc(Long taskId);
}
