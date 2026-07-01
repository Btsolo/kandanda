package com.kandanda.data;

import jakarta.persistence.*;

/**
 * A national team (or club, later). Identified by name.
 *
 * <p>Deliberately minimal. We store only what the strength model needs: an identity.
 * No flags, no FIFA codes, no confederation — those can be added later IF a feature
 * earns them on the Scoreboard. Extract-don't-hoard (NFR, the developer's principle).
 */
@Entity
@Table(name = "team", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    protected Team() { } // JPA requires a no-arg constructor

    public Team(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }

    @Override
    public String toString() {
        return "Team{" + name + "}";
    }
}
