package com.example.gameleaderboard;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlayerService {

    private final PlayerRepository playerRepository;

    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @Transactional(readOnly = true)
    public List<Player> getPlayers() {
        return playerRepository.findAll()
            .stream()
            .map(this::toPlayer)
            .toList();
    }

    @Transactional(readOnly = true)
    public Player getPlayerById(int id) {
        PlayerEntity entity = playerRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Player not found"
            ));

        return toPlayer(entity);
    }

    @Transactional
    public Player addPlayer(Player player) {

        if (playerRepository.existsById(player.id())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Player ID already exists"
            );
        }

        PlayerEntity entity = new PlayerEntity(
            player.id(),
            player.name(),
            player.score()
        );

        PlayerEntity saved = playerRepository.save(entity);

        return toPlayer(saved);
    }

    private Player toPlayer(PlayerEntity entity) {
        return new Player(
            entity.getId(),
            entity.getName(),
            entity.getScore()
        );
    }
}
