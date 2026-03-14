package com.cricket.fantasyleague.entity.table;

import java.util.Random;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "user_overall_stats")
public class UserOverallStats 
{
    @Id
    private Integer id ;

    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User userid ;

    private Double totalpoints ;

    private Double prevpoints ;

    private Integer boosterleft ;

    private Integer transferleft ;

    public UserOverallStats(User userid, Double totalpoints,Double prevpoints, Integer boosterleft, Integer transferleft) 
    {
        this.id = generateId() ;
        this.userid = userid;
        this.prevpoints = prevpoints ;
        this.totalpoints = totalpoints;
        this.boosterleft = boosterleft;
        this.transferleft = transferleft;
    }

    private Integer generateId() 
    {
        Random random = new Random();
        StringBuilder id = new StringBuilder();

        id.append(random.nextInt(9)+1) ;
        
        for (int i = 0; i < 5; i++) {
            int digit = random.nextInt(9); // Generates a random digit between 0 and 9
            id.append(digit);
        }

        return Integer.parseInt(id.toString()) ;
    }
}
