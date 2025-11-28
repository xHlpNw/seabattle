package com.seabattle.server.dto;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class UserRatingDto {
    String username;
    int rating;
}
