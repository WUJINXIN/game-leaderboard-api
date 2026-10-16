package com.example.gameleaderboard;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<PlayerEntity, Integer> {

    List<PlayerEntity> findAllByOrderByScoreDescIdAsc();

}