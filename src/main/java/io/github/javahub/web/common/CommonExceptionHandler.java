package io.github.javahub.web.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class CommonExceptionHandler {

    /** Logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(CommonExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public void exceptionHandler(Exception e){
        Map<String,Object> result = new HashMap<>();
        result.put("code", "-1");
        result.put("msg", e.getMessage());

        LOGGER.error("exceptionHandler", e);
    }
}
