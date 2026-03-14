package com.cricket.fantasyleague.exception;

import java.util.HashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.cricket.fantasyleague.payload.ApiResponse;

@ControllerAdvice
public class GlobalExceptionHandler 
{
    // @ExceptionHandler(CommonException.class)
    // public ResponseEntity<ApiResponse> connectionException(CommonException ce)
    // {
    //     String msg = ce.getMessage(); 
    //     ApiResponse resp = new ApiResponse(msg,false, HttpStatus.SERVICE_UNAVAILABLE.value(),HttpStatus.SERVICE_UNAVAILABLE) ;
    //     return new ResponseEntity<ApiResponse>(resp, HttpStatus.SERVICE_UNAVAILABLE) ;
    // }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse> resourceException(ResourceNotFoundException re)
    {
        String msg = re.getMessage(); 
        ApiResponse resp = new ApiResponse(msg,false, HttpStatus.NOT_FOUND.value(),HttpStatus.NOT_FOUND) ;
        return new ResponseEntity<ApiResponse>(resp, HttpStatus.NOT_FOUND) ;
    }

    // @ExceptionHandler(ParsingException.class)
    // public ResponseEntity<ApiResponse> parsingException(ParsingException pe)
    // {
    //     String msg = pe.getMessage(); 
    //     ApiResponse resp = new ApiResponse(msg,false, HttpStatus.INTERNAL_SERVER_ERROR.value(),HttpStatus.INTERNAL_SERVER_ERROR) ;
    //     return new ResponseEntity<ApiResponse>(resp, HttpStatus.INTERNAL_SERVER_ERROR) ;
    // }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse> badCredential(BadCredentialsException be) 
    {
        String msg = be.getMessage(); 
        ApiResponse resp = new ApiResponse(msg,false, HttpStatus.UNAUTHORIZED.value(),HttpStatus.UNAUTHORIZED) ;
        return new ResponseEntity<ApiResponse>(resp, HttpStatus.UNAUTHORIZED) ;
    }

    @ExceptionHandler(ResourceAlreadyExist.class)
    public ResponseEntity<ApiResponse> accessDenied(ResourceAlreadyExist re) 
    {
        String msg = re.getMessage(); 
        ApiResponse resp = new ApiResponse(msg,false, HttpStatus.CONFLICT.value(),HttpStatus.CONFLICT) ;
        return new ResponseEntity<ApiResponse>(resp, HttpStatus.CONFLICT) ;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> invalidArgumentException(MethodArgumentNotValidException me) 
    {
        HashMap<String,String> allmsg = new HashMap<>() ;
        me.getBindingResult().getAllErrors().forEach((error)->{
            String fieldname = ((FieldError)error).getField();
            String msg = error.getDefaultMessage() ;
            allmsg.put(fieldname, msg) ;
        });

        ApiResponse resp = new ApiResponse(allmsg,false, HttpStatus.BAD_REQUEST.value(),HttpStatus.BAD_REQUEST) ;
        return new ResponseEntity<ApiResponse>(resp, HttpStatus.BAD_REQUEST) ;
    }

    @ExceptionHandler(InvalidTeamException.class)
    public ResponseEntity<ApiResponse> invalidTeamException(InvalidTeamException ie) 
    {
        String msg = ie.getMessage(); 
        ApiResponse resp = new ApiResponse(msg,false, HttpStatus.BAD_REQUEST.value(),HttpStatus.BAD_REQUEST) ;
        return new ResponseEntity<ApiResponse>(resp, HttpStatus.BAD_REQUEST) ;
    }

}
