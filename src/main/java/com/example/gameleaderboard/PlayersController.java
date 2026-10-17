package com.example.gameleaderboard;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

record UpdateScoreRequest(Integer score) {
}

@RestController
public class PlayersController {

	private final PlayerService playerService;

	public PlayersController(PlayerService playerService) {
		this.playerService = playerService;
	}

	@GetMapping("/api/players")
	public List<Player> getPlayers() {
		return playerService.getPlayers();
	}

	@GetMapping("/api/players/{id}")
	public Player getPlayerById(@PathVariable int id) {
		return playerService.getPlayerById(id);
	}

	@PostMapping("/api/players")
	@ResponseStatus(HttpStatus.CREATED)
	public Player addPlayer(@RequestBody Player player) {
		return playerService.addPlayer(player);
	}

	@GetMapping(value = "/api/leaderboard", params = "!limit")
	public List<Player> getLeaderboard() {
		return playerService.getLeaderboard();
	}

	@GetMapping(value = "/api/leaderboard", params = "limit")
	public List<Player> getLeaderboardWithLimit(@RequestParam int limit) {
		return playerService.getLeaderboard(limit);
	}

	@PatchMapping("/api/players/{id}/score")
	public Player updatePlayerScore(@PathVariable int id, @RequestBody UpdateScoreRequest request) {

		if (request.score() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Score is required");
		}

		return playerService.updatePlayerScore(id, request.score());
	}
}
