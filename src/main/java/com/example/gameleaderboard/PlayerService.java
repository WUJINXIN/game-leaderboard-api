package com.example.gameleaderboard;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.springframework.data.domain.PageRequest;

@Service
public class PlayerService {

	private final PlayerRepository playerRepository;
	private final RedisLeaderboardCache leaderboardCache;

	@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Spring-managed dependency injected through constructor")
	public PlayerService(PlayerRepository playerRepository, RedisLeaderboardCache leaderboardCache) {

		this.playerRepository = playerRepository;
		this.leaderboardCache = leaderboardCache;
	}

	@Transactional(readOnly = true)
	public List<Player> getPlayers() {
		return playerRepository.findAll().stream().map(this::toPlayer).toList();
	}

	@Transactional(readOnly = true)
	public List<Player> getLeaderboard() {

		// 1. Try Redis cache
		try {
			if (leaderboardCache.isReady()) {
				return leaderboardCache.getLeaderboard();
			}
		} catch (Exception e) {
			// Redis unavailable or cached data invalid
			System.err.println("Redis cache read failed: " + e.getMessage());
		}

		// 2. Cache Miss: query PostgreSQL
		List<Player> players = playerRepository.findAllByOrderByScoreDescIdAsc().stream().map(this::toPlayer).toList();

		// 3. Rebuild Redis cache
		try {
			leaderboardCache.rebuild(players);
		} catch (Exception e) {
			// Redis failure must not break the leaderboard API
			System.err.println("Redis cache rebuild failed: " + e.getMessage());
		}

		return players;
	}

	@Transactional(readOnly = true)
	public List<Player> getLeaderboard(int limit) {

		if (limit < 1 || limit > 100) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 100");
		}

		return playerRepository.findAllByOrderByScoreDescIdAsc(PageRequest.of(0, limit)).stream().map(this::toPlayer)
				.toList();
	}

	@Transactional(readOnly = true)
	public Player getPlayerById(int id) {
		PlayerEntity entity = playerRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found"));

		return toPlayer(entity);
	}

	@Transactional
	public Player addPlayer(Player player) {

		if (playerRepository.existsById(player.id())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Player ID already exists");
		}

		PlayerEntity saved = playerRepository.save(new PlayerEntity(player.id(), player.name(), player.score()));

		// Invalidate Redis only after PostgreSQL commit
		invalidateLeaderboardAfterCommit();

		return toPlayer(saved);
	}

	@Transactional
	public Player updatePlayerScore(int id, int newScore) {

		PlayerEntity entity = playerRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found"));

		if (newScore < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Score must not be negative");
		}

		entity.setScore(newScore);

		// Invalidate Redis only after PostgreSQL commit
		invalidateLeaderboardAfterCommit();

		return toPlayer(entity);
	}

	private void invalidateLeaderboardAfterCommit() {

		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			throw new IllegalStateException("No active transaction synchronization");
		}

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

			@Override
			public void afterCommit() {
				leaderboardCache.invalidate();
			}
		});
	}

	private Player toPlayer(PlayerEntity entity) {
		return new Player(entity.getId(), entity.getName(), entity.getScore());
	}
}
