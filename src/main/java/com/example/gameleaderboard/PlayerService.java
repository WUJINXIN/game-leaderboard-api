package com.example.gameleaderboard;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlayerService {

    private final List<Player> players = new ArrayList<>(
        List.of(
            new Player(1, "PlayerOne", 1500),
            new Player(2, "PlayerTwo", 1200)
        )
    );

    public List<Player> getPlayers() {
        return List.copyOf(players);
    }

    public Player getPlayerById(int id) {
        return players.stream()
            .filter(player -> player.id() == id)
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Player not found"
            ));
    }

    public Player addPlayer(Player player) {
        boolean exists = players.stream()
            .anyMatch(p -> p.id() == player.id());

        if (exists) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Player ID already exists"
            );
        }

        players.add(player);
        return player;
    }
}
