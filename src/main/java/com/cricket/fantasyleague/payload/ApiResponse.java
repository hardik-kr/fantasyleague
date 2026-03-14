package com.cricket.fantasyleague.payload;

import java.util.HashMap;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse 
{
    private String message ;
    private HashMap<String,String> multimsg ;
    private Boolean success ;
    private int code ;    
    private HttpStatus codetext ;
    

    public ApiResponse(HashMap<String, String> multimsg, Boolean success, int code, HttpStatus codetext) {
        this.success = success;
        this.code = code;
        this.codetext = codetext;
        this.multimsg = multimsg;
    }

    public ApiResponse(String message, Boolean success, int code, HttpStatus codetext) {
        this.message = message;
        this.success = success;
        this.code = code;
        this.codetext = codetext;
    }

}
