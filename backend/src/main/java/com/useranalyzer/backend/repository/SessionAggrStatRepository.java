package com.useranalyzer.backend.repository;

import com.useranalyzer.backend.entity.SessionAggrStat;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionAggrStatRepository extends JpaRepository<SessionAggrStat, Long> {

    @Cacheable("sessionStats")
    SessionAggrStat findByTaskId(Long taskId);
}
