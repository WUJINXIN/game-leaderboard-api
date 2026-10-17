package com.example.gameleaderboard;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.PageRequest;

@Service
public class PlayerService {

	private final PlayerRepository playerRepository;

	public PlayerService(PlayerRepository playerRepository) {
		this.playerRepository = playerRepository;
	}

	@Transactional(readOnly = true)
	public List<Player> getPlayers() {
		return playerRepository.findAll().stream().map(this::toPlayer).toList();
	}

	@Transactional(readOnly = true)
	public List<Player> getLeaderboard() {
		return playerRepository.findAllByOrderByScoreDescIdAsc().stream().map(this::toPlayer).toList();
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

		PlayerEntity entity = new PlayerEntity(player.id(), player.name(), player.score());

		PlayerEntity saved = playerRepository.save(entity);

		return toPlayer(saved);
	}
	
	@Transactional
	public Player updatePlayerScore(int id, int newScore) {

	    PlayerEntity entity = playerRepository.findById(id)
	        .orElseThrow(() -> new ResponseStatusException(
	            HttpStatus.NOT_FOUND,
	            "Player not found"
	        ));

	    if (newScore < 0) {
	        throw new ResponseStatusException(
	            HttpStatus.BAD_REQUEST,
	            "Score must not be negative"
	        );
	    }

	    entity.setScore(newScore);

	    return toPlayer(entity);
	}

	private Player toPlayer(PlayerEntity entity) {
		return new Player(entity.getId(), entity.getName(), entity.getScore());
	}
}
