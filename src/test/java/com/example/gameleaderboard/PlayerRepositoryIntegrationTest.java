package com.example.gameleaderboard;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PlayerRepositoryIntegrationTest {

    @Autowired
    private PlayerRepository repository;

    @Test
    void saveAndFindPlayer() {
        // 1. Create a player
        PlayerEntity player = new PlayerEntity(
            1001, "TestPlayer", 2000
        );

        // 2. Save to PostgreSQL
        repository.saveAndFlush(player);

        // 3. Read from PostgreSQL
        PlayerEntity saved = repository.findById(1001)
            .orElseThrow();

        // 4. Verify the result
        assertEquals("TestPlayer", saved.getName());
        assertEquals(2000, saved.getScore());
    }
}
