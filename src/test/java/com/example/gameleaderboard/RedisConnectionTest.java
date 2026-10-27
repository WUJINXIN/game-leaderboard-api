package com.example.gameleaderboard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RedisConnectionTest {

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Test
	void shouldConnectToRedis() {
		String key = "leaderboard:test:connection";

		redisTemplate.opsForValue().set(key, "hello-redis");

		String value = redisTemplate.opsForValue().get(key);

		assertEquals("hello-redis", value);

		redisTemplate.delete(key);
	}

	@Test
	void shouldRankPlayersWithSortedSet() {

		String key = "leaderboard:test:sorted-set";

		try {
			// ZADD: Add players and scores
			redisTemplate.opsForZSet().add(key, "player:1", 1500);
			redisTemplate.opsForZSet().add(key, "player:2", 2300);
			redisTemplate.opsForZSet().add(key, "player:3", 1800);

			// ZREVRANGE: Get Top 3 players
			Set<String> topPlayers = redisTemplate.opsForZSet().reverseRange(key, 0, 2);

			assertEquals(List.of("player:2", "player:3", "player:1"), new ArrayList<>(topPlayers));

		} finally {
			// Remove test data
			redisTemplate.delete(key);
		}
	}
	
	@Test
	void shouldUpdateScoreAndRanking() {

	    String key = "leaderboard:test:update-score";

	    try {
	        redisTemplate.opsForZSet().add(key, "player:1", 1500);
	        redisTemplate.opsForZSet().add(key, "player:2", 2300);
	        redisTemplate.opsForZSet().add(key, "player:3", 1800);

	        // ZADD: update player:1 score
	        redisTemplate.opsForZSet().add(key, "player:1", 2800);

	        // ZSCORE: verify updated score
	        Double score = redisTemplate.opsForZSet()
	            .score(key, "player:1");

	        assertEquals(2800.0, score);

	        // ZREVRANK: 0 means first place
	        Long rank = redisTemplate.opsForZSet()
	            .reverseRank(key, "player:1");

	        assertEquals(0L, rank);

	    } finally {
	        redisTemplate.delete(key);
	    }
	}
}