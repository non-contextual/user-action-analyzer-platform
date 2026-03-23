package com.useranalyzer.backend.repository;

import com.useranalyzer.backend.entity.Top10Category;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface Top10CategoryRepository extends JpaRepository<Top10Category, Long> {

    @Cacheable("top10Categories")
    List<Top10Category> findByTaskIdOrderByClickCountDesc(Long taskId);
}
