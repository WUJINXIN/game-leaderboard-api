package com.example.gameleaderboard;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

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

	@Test
	@Timeout(45)
	void getLeaderboard_concurrentCacheMiss_shouldRebuildOnlyOnce() throws Exception {

		// Arrange: Prepare test data
		int threadCount = 10;

		List<Player> expectedPlayers = List.of(new Player(3, "PlayerThree", 2600));

		PlayerEntity entity = new PlayerEntity(3, "PlayerThree", 2600);

		// Simulate shared Redis cache state
		AtomicBoolean cacheReady = new AtomicBoolean(false);

		// Coordinate concurrent requests
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch allThreadsSawMiss = new CountDownLatch(threadCount);
		CountDownLatch databaseQueryStarted = new CountDownLatch(1);
		CountDownLatch releaseDatabaseQuery = new CountDownLatch(1);

		// Track unique threads that observed a cache miss
		Set<Thread> cacheMissThreads = ConcurrentHashMap.newKeySet();

		// Mock Redis readiness checks
		when(leaderboardCache.isReady()).thenAnswer(invocation -> {

			boolean ready = cacheReady.get();

			if (!ready && cacheMissThreads.add(Thread.currentThread())) {
				allThreadsSawMiss.countDown();
			}

			return ready;
		});

		// Block the first PostgreSQL query until all threads see a miss
		when(repository.findAllByOrderByScoreDescIdAsc()).thenAnswer(invocation -> {

			databaseQueryStarted.countDown();

			if (!releaseDatabaseQuery.await(20, TimeUnit.SECONDS)) {
				throw new AssertionError("Timed out waiting to release database query");
			}

			return List.of(entity);
		});

		// Simulate successful Redis cache rebuild
		doAnswer(invocation -> {
			cacheReady.set(true);
			return null;
		}).when(leaderboardCache).rebuild(anyList());

		// Return cached leaderboard after rebuilding
		when(leaderboardCache.getLeaderboard()).thenReturn(expectedPlayers);

		ExecutorService executor = Executors.newFixedThreadPool(threadCount);

		List<Future<List<Player>>> futures = new ArrayList<>();

		try {

			// Submit concurrent leaderboard requests
			for (int i = 0; i < threadCount; i++) {
				futures.add(executor.submit(() -> {
					startLatch.await();
					return service.getLeaderboard();
				}));
			}

			// Release all worker threads
			startLatch.countDown();

			// Ensure the first database query is blocked
			assertTrue(databaseQueryStarted.await(20, TimeUnit.SECONDS), "The database query should start");

			// Ensure all threads observe a cache miss
			assertTrue(allThreadsSawMiss.await(20, TimeUnit.SECONDS), "All threads should observe a cache miss");

			// Allow the database query to complete
			releaseDatabaseQuery.countDown();

			// Assert: All threads receive identical leaderboard data
			for (Future<List<Player>> future : futures) {
				assertEquals(expectedPlayers, future.get(20, TimeUnit.SECONDS));
			}

			// Verify PostgreSQL is queried exactly once
			verify(repository, times(1)).findAllByOrderByScoreDescIdAsc();

			// Verify Redis cache is rebuilt exactly once
			verify(leaderboardCache, times(1)).rebuild(expectedPlayers);

		} finally {

			// Prevent blocked worker threads from being left behind
			releaseDatabaseQuery.countDown();
			startLatch.countDown();

			executor.shutdownNow();
			assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Worker threads should terminate");
		}
	}

}
