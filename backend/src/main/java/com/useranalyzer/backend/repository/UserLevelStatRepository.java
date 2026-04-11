package com.useranalyzer.backend.repository;

import com.useranalyzer.backend.entity.UserLevelStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserLevelStatRepository extends JpaRepository<UserLevelStat, Long> {

    List<UserLevelStat> findByTaskIdOrderByUserCountDesc(Long taskId);
}
