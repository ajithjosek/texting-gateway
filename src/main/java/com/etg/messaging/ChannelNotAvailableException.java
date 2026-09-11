package com.etg.messaging;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ChannelNotAvailableException extends RuntimeException {
  public ChannelNotAvailableException(String message) { super(message); }
}
