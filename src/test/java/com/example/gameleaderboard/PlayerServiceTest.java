package com.example.gameleaderboard;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerServiceTest {

	private PlayerRepository repository;
	private PlayerService service;
	private RedisLeaderboardCache leaderboardCache;

	@BeforeEach
	void setUp() {
		repository = mock(PlayerRepository.class);
		leaderboardCache = mock(RedisLeaderboardCache.class);

		service = new PlayerService(repository, leaderboardCache);
	}

	@Test
	void getPlayerById_success() {
		when(repository.findById(1)).thenReturn(Optional.of(new PlayerEntity(1, "PlayerOne", 1500)));

		Player player = service.getPlayerById(1);

		assertEquals("PlayerOne", player.name());
		assertEquals(1500, player.score());

		verify(repository).findById(1);
	}

	@Test
	void getPlayerById_notFound() {
		when(repository.findById(99)).thenReturn(Optional.empty());

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.getPlayerById(99));

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
	}

	@Test
	void addPlayer_duplicateId() {
		when(repository.existsById(1)).thenReturn(true);

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.addPlayer(new Player(1, "AnotherPlayer", 2000)));

		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

		verify(repository, never()).save(any(PlayerEntity.class));
	}

	@Test
	void getPlayers_success() {
		when(repository.findAll())
				.thenReturn(List.of(new PlayerEntity(1, "PlayerOne", 1500), new PlayerEntity(2, "PlayerTwo", 1200)));

		List<Player> players = service.getPlayers();

		assertEquals(2, players.size());
		assertEquals("PlayerOne", players.get(0).name());
		assertEquals("PlayerTwo", players.get(1).name());
	}

	@Test
	void addPlayer_success() {

		TransactionSynchronizationManager.initSynchronization();

		try {
			Player input = new Player(3, "PlayerThree", 1800);

			when(repository.existsById(3)).thenReturn(false);

			when(repository.save(any(PlayerEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

			Player result = service.addPlayer(input);

			assertEquals(3, result.id());
			assertEquals("PlayerThree", result.name());
			assertEquals(1800, result.score());

			verify(repository).save(any(PlayerEntity.class));

			// The cache should NOT be invalidated before commit
			verify(leaderboardCache, never()).invalidate();

			// Simulate successful transaction commit
			for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
				synchronization.afterCommit();
			}

			// Cache invalidation happens after commit
			verify(leaderboardCache).invalidate();

		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void addPlayer_shouldSucceedWhenRedisInvalidationFails() {

		TransactionSynchronizationManager.initSynchronization();

		try {
			Player input = new Player(4, "PlayerFour", 2200);

			when(repository.existsById(4)).thenReturn(false);

			when(repository.save(any(PlayerEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

			doThrow(new RedisConnectionFailureException("Redis unavailable")).when(leaderboardCache).invalidate();

			Player result = service.addPlayer(input);

			assertEquals(4, result.id());
			assertEquals("PlayerFour", result.name());
			assertEquals(2200, result.score());

			verify(leaderboardCache, never()).invalidate();

			// Simulate successful PostgreSQL commit
			assertDoesNotThrow(() -> {
				for (TransactionSynchronization synchronization : TransactionSynchronizationManager
						.getSynchronizations()) {
					synchronization.afterCommit();
				}
			});

			verify(leaderboardCache).invalidate();

		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void getLeaderboard_cacheMiss_shouldQueryDatabaseAndRebuildCache() {

		// Arrange: Simulate a Redis cache miss
		when(leaderboardCache.isReady()).thenReturn(false);

		// Mock the player data returned by PostgreSQL
		PlayerEntity entity = mock(PlayerEntity.class);

		when(entity.getId()).thenReturn(3);
		when(entity.getName()).thenReturn("PlayerThree");
		when(entity.getScore()).thenReturn(2600);

		when(repository.findAllByOrderByScoreDescIdAsc()).thenReturn(List.of(entity));

		// Act: Retrieve the leaderboard
		List<Player> result = service.getLeaderboard();

		// Assert: Verify the returned leaderboard data
		assertEquals(1, result.size());
		assertEquals(3, result.get(0).id());
		assertEquals("PlayerThree", result.get(0).name());
		assertEquals(2600, result.get(0).score());

		// Verify that PostgreSQL is queried exactly once
		verify(repository, times(1)).findAllByOrderByScoreDescIdAsc();

		// Verify that the Redis cache is rebuilt exactly once
		verify(leaderboardCache, times(1)).rebuild(result);

		// Verify that the existing Redis leaderboard is never read
		verify(leaderboardCache, never()).getLeaderboard();
	}

	@Test
	void getLeaderboard_cacheHit_shouldSkipDatabase() {

		// Arrange: Simulate a Redis cache hit
		when(leaderboardCache.isReady()).thenReturn(true);

		List<Player> cachedPlayers = List.of(new Player(3, "PlayerThree", 2600));

		when(leaderboardCache.getLeaderboard()).thenReturn(cachedPlayers);

		// Act: Retrieve the leaderboard
		List<Player> result = service.getLeaderboard();

		// Assert: Verify the returned leaderboard data
		assertEquals(cachedPlayers, result);

		// Verify that the leaderboard is retrieved from Redis exactly once
		verify(leaderboardCache, times(1)).getLeaderboard();

		// Verify that PostgreSQL is never queried
		verify(repository, never()).findAllByOrderByScoreDescIdAsc();

		// Verify that the Redis cache is not rebuilt
		verify(leaderboardCache, never()).rebuild(anyList());
	}
}
