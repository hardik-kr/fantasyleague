package com.cricket.fantasyleague.payload.jwtdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JwtResponse 
{
    private String jwttoken ;
    private String username ;    
}
