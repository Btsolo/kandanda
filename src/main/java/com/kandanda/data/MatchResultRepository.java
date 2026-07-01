package com.kandanda.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Repository for matches. {@code findByTournamentOrderByDateAsc} returns a
 * tournament's matches in chronological order — important for the point-in-time
 * rule (we process matches in the order they were actually played).
 */
public interface MatchResultRepository extends JpaRepository<MatchResult, Long> {
    List<MatchResult> findByTournamentOrderByDateAsc(String tournament);
    long countByTournament(String tournament);
}
