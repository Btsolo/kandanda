package com.kandanda.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads an openfootball worldcup.json file into the database as {@link Team} and
 * {@link MatchResult} rows.
 *
 * <p>This is the "pull data, extract what we need, store only that" layer. The source
 * JSON has goalscorers, stadiums, half-time scores, etc. — we read only teams and the
 * 90-minute full-time score. Everything else is discarded at the door.
 *
 * <p>Key decision, enforced here: we read {@code score.ft} (full-time / 90 min) and
 * ignore {@code score.et} (extra time) and {@code score.p} (penalties). See
 * {@link MatchResult} for why (shootouts are noise the goal model must not learn).
 *
 * <p>A match with no {@code ft} score (an unplayed future fixture) is skipped — this
 * matters for the live 2026 file, which contains fixtures not yet played.
 */
@Service
public class TournamentLoader {

    private final TeamRepository teamRepo;
    private final MatchResultRepository matchRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public TournamentLoader(TeamRepository teamRepo, MatchResultRepository matchRepo) {
        this.teamRepo = teamRepo;
        this.matchRepo = matchRepo;
    }

    /**
     * Load a tournament from a classpath JSON resource.
     *
     * @param resourcePath e.g. "data/worldcup-2022.json"
     * @param tournamentTag short tag stored on each match, e.g. "WC-2022"
     * @return number of matches actually stored (played matches only)
     */
    public int load(String resourcePath, String tournamentTag) {
        // Idempotency: don't double-load the same tournament.
        if (matchRepo.countByTournament(tournamentTag) > 0) {
            return 0;
        }

        JsonNode root;
        try (InputStream in = new ClassPathResource(resourcePath).getInputStream()) {
            root = mapper.readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read " + resourcePath, e);
        }

        // Cache teams in-memory during the load so we reuse one Team row per name.
        Map<String, Team> teamCache = new HashMap<>();

        int stored = 0;
        for (JsonNode m : root.path("matches")) {
            JsonNode ft = m.path("score").path("ft");
            // Skip unplayed fixtures (no full-time score yet).
            if (!ft.isArray() || ft.size() < 2) {
                continue;
            }

            String homeName = m.path("team1").asText();
            String awayName = m.path("team2").asText();
            if (homeName.isEmpty() || awayName.isEmpty()) {
                continue; // e.g. knockout placeholders like "Winner Group A"
            }

            Team home = teamCache.computeIfAbsent(homeName, this::findOrCreateTeam);
            Team away = teamCache.computeIfAbsent(awayName, this::findOrCreateTeam);

            int hg = ft.get(0).asInt();
            int ag = ft.get(1).asInt();
            String round = m.path("round").asText("");
            LocalDate date = m.hasNonNull("date") ? LocalDate.parse(m.get("date").asText()) : null;

            // Optional xG: an "xg":[home,away] array if the dataset carries it.
            Double hxg = null, axg = null;
            JsonNode xg = m.path("xg");
            if (xg.isArray() && xg.size() >= 2) {
                hxg = xg.get(0).asDouble();
                axg = xg.get(1).asDouble();
            }

            matchRepo.save(new MatchResult(tournamentTag, round, date, home, away, hg, ag, hxg, axg));
            stored++;
        }
        return stored;
    }

    private Team findOrCreateTeam(String name) {
        return teamRepo.findByName(name).orElseGet(() -> teamRepo.save(new Team(name)));
    }
}
