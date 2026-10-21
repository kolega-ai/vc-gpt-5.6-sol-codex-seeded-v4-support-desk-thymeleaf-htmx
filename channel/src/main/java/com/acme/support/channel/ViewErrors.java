package com.acme.support.channel;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
class ViewErrors {
 @ExceptionHandler(HttpClientErrorException.Forbidden.class) ModelAndView forbidden(){return view(HttpStatus.FORBIDDEN);}
 @ExceptionHandler(HttpClientErrorException.NotFound.class) ModelAndView missing(){return view(HttpStatus.NOT_FOUND);}
 @ExceptionHandler({HttpClientErrorException.BadRequest.class,IllegalArgumentException.class}) ModelAndView invalid(){return view(HttpStatus.BAD_REQUEST);}
 private static ModelAndView view(HttpStatus status){ModelAndView result=new ModelAndView("error");result.setStatus(status);return result;}
}
