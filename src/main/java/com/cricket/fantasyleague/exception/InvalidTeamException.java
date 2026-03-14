package com.cricket.fantasyleague.exception;

public class InvalidTeamException extends RuntimeException
{
    String resourceName ;  
    public InvalidTeamException(String resourceName)
    {
        super(resourceName) ;
    }  
}
