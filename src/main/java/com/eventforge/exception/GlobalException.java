package com.eventforge.exception;

import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.springframework.http.HttpStatus;



@Getter
public class GlobalException extends RuntimeException{

   private final int httpStatus = HttpServletResponse.SC_CONFLICT;

    public GlobalException(String message){
        super(message);

    }
}
