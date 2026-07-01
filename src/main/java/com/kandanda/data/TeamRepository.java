package com.kandanda.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * Repository for teams. Spring Data generates the implementation at runtime:
 * we declare WHAT we want (findByName) and it writes the SQL.
 */
public interface TeamRepository extends JpaRepository<Team, Long> {
    Optional<Team> findByName(String name);
}
