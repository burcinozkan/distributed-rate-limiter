package com.burcin.ratelimiter.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class TestController {

    @GetMapping("/api/test")
    public Mono<String> test() {
        return Mono.just("Request Successful! You haven't been limited yet. :)");
    }
}