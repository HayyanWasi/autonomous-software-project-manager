package com.autonomouspm.core;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA Repository for persisting {@link ProjectStateEntity}.
 */
@Repository
public interface ProjectStateRepository extends JpaRepository<ProjectStateEntity, String> {
}
