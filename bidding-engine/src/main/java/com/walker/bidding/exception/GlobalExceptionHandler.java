package com.walker.bidding.exception;

import com.walker.bidding.auction.AuctionService;
import org.springframework.beans.factory.parsing.Problem;
import com.walker.bidding.exception.ConcurrentBidException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ConcurrentBidException.class)
    public ProblemDetail handleConcurrentModification(ConcurrentBidException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "High traffic. Please try again.");
    }
}
