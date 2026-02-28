package com.walker.bidding.exception;

public class ConcurrentBidException extends RuntimeException{
    public ConcurrentBidException(String message) {
        super(message);
    }
}
