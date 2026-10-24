package com.example.gameleaderboard;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

@Component
public class RedisLeaderboardCache {

	private static final String KEY = "leaderboard:cache:v1";

	private static final String NAMES_KEY = KEY + ":names";

	private static final String READY_KEY = KEY + ":ready";

	private static final Duration CACHE_TTL = Duration.ofSeconds(60);

	private final StringRedisTemplate redisTemplate;

	public RedisLeaderboardCache(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	// ZADD: Store a player's score in Redis
	public void putScore(int playerId, int score) {
		redisTemplate.opsForZSet().add(KEY, "player:" + playerId, score);
	}

	// DEL: Remove the cached leaderboard
	public void invalidate() {
		redisTemplate.delete(List.of(KEY, NAMES_KEY, READY_KEY));
	}

	public List<Player> getLeaderboard() {

		Set<ZSetOperations.TypedTuple<String>> entries = redisTemplate.opsForZSet().rangeWithScores(KEY, 0, -1);

		if (entries == null) {
			return List.of();
		}

		return entries.stream().map(entry -> {

			String member = entry.getValue();

			int id = Integer.parseInt(member.substring("player:".length()));

			int score = entry.getScore().intValue();

			Object name = redisTemplate.opsForHash().get(NAMES_KEY, member);

			if (!(name instanceof String playerName)) {
				throw new IllegalStateException("Missing cached player name: " + member);
			}

			return new Player(id, playerName, score);
		}).sorted(Comparator.comparingInt(Player::score).reversed().thenComparingInt(Player::id)).toList();
	}

	public boolean isReady() {
		return Boolean.TRUE.equals(redisTemplate.hasKey(READY_KEY));
	}

	public void rebuild(List<Player> players) {

		redisTemplate.execute(new SessionCallback<Object>() {

			@Override
			public <K, V> Object execute(RedisOperations<K, V> operations) {

				operations.multi();

				// Remove previous cache
				redisTemplate.delete(KEY);
				redisTemplate.delete(NAMES_KEY);
				redisTemplate.delete(READY_KEY);

				// Rebuild scores and names
				for (Player player : players) {

					String member = "player:" + player.id();

					redisTemplate.opsForZSet().add(KEY, member, player.score());

					redisTemplate.opsForHash().put(NAMES_KEY, member, player.name());
				}

				// Set TTL for non-empty leaderboard
				if (!players.isEmpty()) {
					redisTemplate.expire(KEY, CACHE_TTL);
					redisTemplate.expire(NAMES_KEY, CACHE_TTL);
				}

				// Set Ready Marker
				redisTemplate.opsForValue().set(READY_KEY, "1", CACHE_TTL);

				return operations.exec();
			}
		});
	}

	public void putName(int playerId, String name) {

		redisTemplate.opsForHash().put(NAMES_KEY, "player:" + playerId, name);
	}
}