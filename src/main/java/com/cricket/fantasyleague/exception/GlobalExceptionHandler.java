package com.cricket.fantasyleague.exception;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse> resourceException(ResourceNotFoundException re)
    {
        String msg = re.getMessage(); 
        ApiResponse resp = new ApiResponse(msg, false, HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND);
        return new ResponseEntity<>(resp, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse> badCredential(BadCredentialsException be) 
    {
        String msg = be.getMessage(); 
        ApiResponse resp = new ApiResponse(msg, false, HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED);
        return new ResponseEntity<>(resp, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(ResourceAlreadyExist.class)
    public ResponseEntity<ApiResponse> resourceAlreadyExist(ResourceAlreadyExist re) 
    {
        String msg = re.getMessage(); 
        ApiResponse resp = new ApiResponse(msg, false, HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT);
        return new ResponseEntity<>(resp, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> invalidArgumentException(MethodArgumentNotValidException me) 
    {
        HashMap<String, String> allmsg = new HashMap<>();
        me.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldname = ((FieldError) error).getField();
            String msg = error.getDefaultMessage();
            allmsg.put(fieldname, msg);
        });
        ApiResponse resp = new ApiResponse(allmsg, false, HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
        return new ResponseEntity<>(resp, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidTeamException.class)
    public ResponseEntity<ApiResponse> invalidTeamException(InvalidTeamException ie) 
    {
        String msg = ie.getMessage(); 
        ApiResponse resp = new ApiResponse(msg, false, HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
        return new ResponseEntity<>(resp, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(CommonException.class)
    public ResponseEntity<ApiResponse> commonException(CommonException ce)
    {
        logger.error("CommonException: {}", ce.getMessage(), ce);
        ApiResponse resp = new ApiResponse(ce.getMessage(), false,
                HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR);
        return new ResponseEntity<>(resp, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse> illegalArgument(IllegalArgumentException ie)
    {
        ApiResponse resp = new ApiResponse(ie.getMessage(), false, HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
        return new ResponseEntity<>(resp, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse> illegalState(IllegalStateException ie)
    {
        ApiResponse resp = new ApiResponse(ie.getMessage(), false, HttpStatus.TOO_MANY_REQUESTS.value(), HttpStatus.TOO_MANY_REQUESTS);
        return new ResponseEntity<>(resp, HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> catchAll(Exception ex)
    {
        logger.error("Unhandled exception: {}", ex.getMessage(), ex);
        String msg = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        ApiResponse resp = new ApiResponse(msg, false,
                HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR);
        return new ResponseEntity<>(resp, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
