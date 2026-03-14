package com.cricket.fantasyleague.exception;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommonException extends RuntimeException 
{
    String resourceName ;  
    public CommonException(String resourceName)
    {
        super(resourceName) ;
    } 

    String fieldName ;
    String fieldValue ;
    
    public CommonException(String resourceName, String fieldName, String fieldValue) 
    {
        super(String.format("%s not found with %s : %s",resourceName,fieldName,fieldValue)) ;
        this.resourceName = resourceName;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }
}
