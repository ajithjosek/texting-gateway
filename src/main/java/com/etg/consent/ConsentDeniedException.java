package com.etg.consent;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class ConsentDeniedException extends RuntimeException {
  public ConsentDeniedException(String message) { super(message); }
}
