package com.example.gameleaderboard;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class PlayerServiceTest {

    private final PlayerService service = new PlayerService();

    @Test
    void getPlayerById_success() {
        Player player = service.getPlayerById(1);

        assertEquals("PlayerOne", player.name());
        assertEquals(1500, player.score());
    }

    @Test
    void getPlayerById_notFound() {
        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.getPlayerById(99)
        );

        assertEquals(HttpStatus.NOT_FOUND,
                     exception.getStatusCode());
    }

    @Test
    void addPlayer_duplicateId() {
        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.addPlayer(
                new Player(1, "AnotherPlayer", 2000)
            )
        );

        assertEquals(HttpStatus.CONFLICT,
                     exception.getStatusCode());
    }
}
