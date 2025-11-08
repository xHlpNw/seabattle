package com.seabattle.server.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ShotRequest {
    public int x;
    public int y;
}
