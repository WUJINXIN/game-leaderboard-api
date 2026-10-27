package com.example.gameleaderboard;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlayerRepositoryIntegrationTest {

	@Autowired
	private PlayerRepository repository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void saveAndFindPlayer() {
		// 1. Create a player
		PlayerEntity player = new PlayerEntity(1001, "TestPlayer", 2000);

		// 2. Save to PostgreSQL
		repository.saveAndFlush(player);

		// 3. Clear JPA first-level cache
		entityManager.clear();

		// 4. Read from PostgreSQL
		PlayerEntity saved = repository.findById(1001).orElseThrow();

		// 5. Verify the result
		assertEquals("TestPlayer", saved.getName());
		assertEquals(2000, saved.getScore());
	}

	@Test
	void findLeaderboard_ordersByScoreDescAndIdAsc() {
		// 1. Prepare test players
		repository.saveAndFlush(new PlayerEntity(3001, "Alice", 2000));
		repository.saveAndFlush(new PlayerEntity(3002, "Bob", 2000));
		repository.saveAndFlush(new PlayerEntity(3003, "Charlie", 3000));

		// 2. Clear JPA cache
		entityManager.clear();

		// 3. Query leaderboard
		var leaderboard = repository.findAllByOrderByScoreDescIdAsc();

		// Verify the ranking order of the test players
		var testPlayerIds = leaderboard.stream().map(PlayerEntity::getId)
				.filter(id -> id == 3001 || id == 3002 || id == 3003).toList();

		assertEquals(List.of(3003, 3001, 3002), testPlayerIds);
	}

	@Test
	void shouldUseConfiguredDatabase() {
		String databaseName = (String) entityManager.createNativeQuery("SELECT current_database()").getSingleResult();

		String expectedDatabase = System.getenv("DB_URL") == null ? "game_leaderboard_flyway_test" : "game_leaderboard";

		assertEquals(expectedDatabase, databaseName);
	}
}