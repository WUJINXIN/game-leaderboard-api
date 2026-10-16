package com.example.gameleaderboard;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PlayerRepositoryIntegrationTest {

    @Autowired
    private PlayerRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void saveAndFindPlayer() {
        // 1. Create a player
        PlayerEntity player = new PlayerEntity(
                1001, "TestPlayer", 2000);

        // 2. Save to PostgreSQL
        repository.saveAndFlush(player);

        // 3. Clear JPA first-level cache
        entityManager.clear();

        // 4. Read from PostgreSQL
        PlayerEntity saved = repository.findById(1001)
                .orElseThrow();

        // 5. Verify the result
        assertEquals("TestPlayer", saved.getName());
        assertEquals(2000, saved.getScore());
    }

    @Test
    void findLeaderboard_ordersByScoreDescAndIdAsc() {
        // 1. Prepare test players
        repository.saveAndFlush(
                new PlayerEntity(3001, "Alice", 2000));
        repository.saveAndFlush(
                new PlayerEntity(3002, "Bob", 2000));
        repository.saveAndFlush(
                new PlayerEntity(3003, "Charlie", 3000));

        // 2. Clear JPA cache
        entityManager.clear();

        // 3. Query leaderboard
        var leaderboard = repository.findAllByOrderByScoreDescIdAsc();

        // 4. Verify ranking order
        assertEquals(3003, leaderboard.get(0).getId());
        assertEquals(3001, leaderboard.get(1).getId());
        assertEquals(3002, leaderboard.get(2).getId());
    }
}