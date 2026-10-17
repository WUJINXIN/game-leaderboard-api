package com.example.gameleaderboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlayersControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PlayerRepository repository;

	@Test
	void createPlayer_returns201() throws Exception {
		String json = """
				{
				    "id": 2001,
				    "name": "ApiTestPlayer",
				    "score": 2500
				}
				""";

		mockMvc.perform(post("/api/players").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(2001))
				.andExpect(jsonPath("$.name").value("ApiTestPlayer")).andExpect(jsonPath("$.score").value(2500));

		entityManager.flush();
		entityManager.clear();
	}

	@Test
	void getPlayerById_returns200() throws Exception {
		// 1. Prepare test data
		PlayerEntity player = new PlayerEntity(2002, "GetTestPlayer", 3000);

		repository.saveAndFlush(player);

		// 2. Clear JPA cache
		entityManager.clear();

		// 3. Send GET request
		mockMvc.perform(get("/api/players/2002")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(2002))
				.andExpect(jsonPath("$.name").value("GetTestPlayer")).andExpect(jsonPath("$.score").value(3000));
	}

	@Test
	void getPlayerById_returns404() throws Exception {
		// Make sure the player does not exist
		repository.deleteById(9999);
		repository.flush();
		entityManager.clear();

		// Send GET request
		mockMvc.perform(get("/api/players/9999")).andExpect(status().isNotFound());
	}

	@Test
	void createPlayer_returns409_whenIdExists() throws Exception {
		// 1. Prepare an existing player
		PlayerEntity player = new PlayerEntity(2003, "ExistingPlayer", 3500);

		repository.saveAndFlush(player);
		entityManager.clear();

		// 2. Attempt to create another player with the same ID
		String json = """
				{
				    "id": 2003,
				    "name": "DuplicatePlayer",
				    "score": 4000
				}
				""";

		mockMvc.perform(post("/api/players").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isConflict());
	}

	@Test
	void getLeaderboard_returnsPlayersInRankOrder() throws Exception {
		// 1. Prepare test players
		repository.saveAndFlush(new PlayerEntity(4001, "Alice", 2000));
		repository.saveAndFlush(new PlayerEntity(4002, "Bob", 3000));
		repository.saveAndFlush(new PlayerEntity(4003, "Charlie", 2000));

		// 2. Clear JPA first-level cache
		entityManager.clear();

		// 3. Request leaderboard
		mockMvc.perform(get("/api/leaderboard")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(4002))
				.andExpect(jsonPath("$[0].name").value("Bob")).andExpect(jsonPath("$[1].id").value(4001))
				.andExpect(jsonPath("$[1].name").value("Alice")).andExpect(jsonPath("$[2].id").value(4003))
				.andExpect(jsonPath("$[2].name").value("Charlie"));
	}

	@Test
	void getLeaderboardWithLimit_returnsTop2() throws Exception {
		repository.saveAndFlush(new PlayerEntity(5001, "Alice", 1000));
		repository.saveAndFlush(new PlayerEntity(5002, "Bob", 3000));
		repository.saveAndFlush(new PlayerEntity(5003, "Charlie", 2000));

		entityManager.clear();

		mockMvc.perform(get("/api/leaderboard").param("limit", "2")).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].id").value(5002))
				.andExpect(jsonPath("$[1].id").value(5003));
	}

	@Test
	void getLeaderboardWithLimit_returns400_whenLimitIsZero() throws Exception {

		mockMvc.perform(get("/api/leaderboard").param("limit", "0")).andExpect(status().isBadRequest());
	}

	@Test
	void getLeaderboardWithLimit_returns400_whenLimitIsTooLarge() throws Exception {

		mockMvc.perform(get("/api/leaderboard").param("limit", "101")).andExpect(status().isBadRequest());
	}

	@Test
	void getLeaderboardWithLimit_returns400_whenLimitIsNotNumber() throws Exception {

		mockMvc.perform(get("/api/leaderboard").param("limit", "abc")).andExpect(status().isBadRequest());
	}

	@Test
	void updatePlayerScore_returns200() throws Exception {

		// 1. Prepare existing player
		repository.saveAndFlush(new PlayerEntity(6001, "UpdateTestPlayer", 1800));

		entityManager.clear();

		// 2. Send PATCH request
		String json = """
				{
				    "score": 2500
				}
				""";

		mockMvc.perform(patch("/api/players/6001/score").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(6001))
				.andExpect(jsonPath("$.name").value("UpdateTestPlayer")).andExpect(jsonPath("$.score").value(2500));

		// 3. Force Hibernate to execute UPDATE
		entityManager.flush();
		entityManager.clear();

		// 4. Verify updated score from PostgreSQL
		PlayerEntity updated = repository.findById(6001).orElseThrow();

		assertEquals(2500, updated.getScore());
	}

	@Test
	void updatePlayerScore_negativeScore_returns400() throws Exception {

		repository.saveAndFlush(new PlayerEntity(6002, "NegativeScorePlayer", 1800));

		mockMvc.perform(patch("/api/players/6002/score").contentType(MediaType.APPLICATION_JSON).content("""
				{
				    "score": -100
				}
				""")).andExpect(status().isBadRequest());

		entityManager.flush();
		entityManager.clear();

		PlayerEntity player = repository.findById(6002).orElseThrow();

		assertEquals(1800, player.getScore());
	}

	@Test
	void updatePlayerScore_playerNotFound_returns404() throws Exception {

		int missingId = 999999;

		// Make sure the test player does not exist
		repository.deleteById(missingId);
		repository.flush();

		// Send PATCH to a nonexistent player
		mockMvc.perform(patch("/api/players/{id}/score", missingId).contentType(MediaType.APPLICATION_JSON).content("""
				{
				    "score": 2500
				}
				""")).andExpect(status().isNotFound());
	}

	@Test
	void updatePlayerScore_missingScore_returns400() throws Exception {

		repository.saveAndFlush(new PlayerEntity(6003, "MissingScorePlayer", 1800));

		// JSON contains no score field
		mockMvc.perform(patch("/api/players/6003/score").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());

		// Verify original score is unchanged
		entityManager.flush();
		entityManager.clear();

		PlayerEntity player = repository.findById(6003).orElseThrow();

		assertEquals(1800, player.getScore());
	}

}
