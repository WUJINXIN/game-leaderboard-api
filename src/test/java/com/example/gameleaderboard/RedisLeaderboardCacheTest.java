package com.example.gameleaderboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class RedisLeaderboardCacheTest {

	@Autowired
	private RedisLeaderboardCache cache;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Test
	void shouldPutScoreIntoRedis() {

		String key = "leaderboard:cache:v1";

		try {
			cache.invalidate();

			cache.putScore(1001, 2500);

			Double score = redisTemplate.opsForZSet().score(key, "player:1001");

			assertEquals(2500.0, score);

		} finally {
			cache.invalidate();
		}
	}

	@Test
	void shouldInvalidateLeaderboardCache() {

		String key = "leaderboard:cache:v1";

		try {
			cache.putScore(1002, 1800);

			cache.invalidate();

			assertFalse(redisTemplate.hasKey(key));

		} finally {
			cache.invalidate();
		}
	}

	@Test
	void shouldReadLeaderboardWithCorrectTieBreaking() {

	    try {
	        cache.invalidate();

	        // Store scores in Redis Sorted Set
	        cache.putScore(3, 2800);
	        cache.putScore(1, 2800);
	        cache.putScore(2, 2300);

	        // Store names in Redis Hash
	        cache.putName(3, "Charlie");
	        cache.putName(1, "Alice");
	        cache.putName(2, "Bob");

	        List<Player> leaderboard = cache.getLeaderboard();

	        assertEquals(3, leaderboard.size());

	        // Verify ranking: score DESC, id ASC
	        assertEquals(1, leaderboard.get(0).id());
	        assertEquals(3, leaderboard.get(1).id());
	        assertEquals(2, leaderboard.get(2).id());

	        // Verify scores
	        assertEquals(2800, leaderboard.get(0).score());
	        assertEquals(2800, leaderboard.get(1).score());
	        assertEquals(2300, leaderboard.get(2).score());

	        // Verify names
	        assertEquals("Alice", leaderboard.get(0).name());
	        assertEquals("Charlie", leaderboard.get(1).name());
	        assertEquals("Bob", leaderboard.get(2).name());

	    } finally {
	        cache.invalidate();
	    }
	}

	@Test
	void shouldRebuildLeaderboardWithTTLAndReadyMarker() {

		String leaderboardKey = "leaderboard:cache:v1";
		String readyKey = "leaderboard:cache:v1:ready";

		try {
			cache.invalidate();

			// Rebuild leaderboard
			cache.rebuild(List.of(new Player(1, "Alice", 2800), new Player(2, "Bob", 2300)));

			List<Player> leaderboard = cache.getLeaderboard();

			assertEquals(2, leaderboard.size());

			assertEquals(new Player(1, "Alice", 2800), leaderboard.get(0));

			assertEquals(new Player(2, "Bob", 2300), leaderboard.get(1));

			String namesKey = "leaderboard:cache:v1:names";

			assertEquals("Alice", redisTemplate.opsForHash().get(namesKey, "player:1"));

			assertEquals("Bob", redisTemplate.opsForHash().get(namesKey, "player:2"));

			// Verify Ready Marker
			assertTrue(cache.isReady());

			// Verify Redis Sorted Set
			assertEquals(2800.0, redisTemplate.opsForZSet().score(leaderboardKey, "player:1"));

			assertEquals(2300.0, redisTemplate.opsForZSet().score(leaderboardKey, "player:2"));

			// Verify TTL
			Long leaderboardTTL = redisTemplate.getExpire(leaderboardKey);
			Long readyTTL = redisTemplate.getExpire(readyKey);
			Long namesTTL = redisTemplate.getExpire(namesKey);

			assertTrue(leaderboardTTL > 0 && leaderboardTTL <= 60);
			assertTrue(readyTTL > 0 && readyTTL <= 60);
			assertNotNull(namesTTL);
			assertTrue(namesTTL > 0 && namesTTL <= 60);

		} finally {
			cache.invalidate();
		}
	}

	@Test
	void shouldDistinguishEmptyLeaderboardFromCacheMiss() {

		String leaderboardKey = "leaderboard:cache:v1";
		String readyKey = "leaderboard:cache:v1:ready";

		try {
			// Initially: Cache Miss
			cache.invalidate();
			assertFalse(cache.isReady());

			// Rebuild an empty leaderboard
			cache.rebuild(List.of());

			// Cache is ready, even though leaderboard is empty
			assertTrue(cache.isReady());
			assertFalse(redisTemplate.hasKey(leaderboardKey));
			assertTrue(redisTemplate.hasKey(readyKey));

			// Reading should return an empty list
			assertTrue(cache.getLeaderboard().isEmpty());

			// After invalidation: Cache Miss again
			cache.invalidate();
			assertFalse(cache.isReady());

		} finally {
			cache.invalidate();
		}
	}

	@Test
	void shouldStorePlayerNameInRedisHash() {

		String namesKey = "leaderboard:cache:v1:names";

		try {
			redisTemplate.delete(namesKey);

			cache.putName(1, "Alice");

			Object name = redisTemplate.opsForHash().get(namesKey, "player:1");

			assertEquals("Alice", name);

		} finally {
			redisTemplate.delete(namesKey);
		}
	}
}