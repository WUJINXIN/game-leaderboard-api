package com.example.gameleaderboard;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/api/leaderboard")
    public List<Player> getLeaderboard() {
        return playerService.getLeaderboard();
    }
}
