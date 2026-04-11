package com.useranalyzer.backend.repository;

import com.useranalyzer.backend.entity.ProductAssociation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductAssociationRepository extends JpaRepository<ProductAssociation, Long> {

    List<ProductAssociation> findByTaskIdOrderByConfidenceDescLiftDesc(Long taskId);
}
