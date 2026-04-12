package com.cricket.fantasyleague.payload.response;

import com.cricket.fantasyleague.entity.enums.PlayerType;

public record PlayerBrief(Integer id, String name, PlayerType role) {
}
