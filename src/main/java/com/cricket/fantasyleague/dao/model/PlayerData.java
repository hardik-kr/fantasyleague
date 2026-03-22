package com.cricket.fantasyleague.dao.model;

import com.cricket.fantasyleague.entity.enums.PlayerType;

public record PlayerData(
        Integer id,
        String name,
        PlayerType role
) {
}
