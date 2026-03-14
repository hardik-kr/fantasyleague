package com.cricket.fantasyleague.exception;

public class ResourceAlreadyExist extends RuntimeException
{
    String resourceName ;  
    String fieldName ;
    String fieldValue ;

    public ResourceAlreadyExist(String resourceName, String fieldName, String fieldValue) {
        super(String.format(resourceName,fieldName,fieldValue));
    }
}
