
package com.example.webclient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/proxy")
public class ProxyController {

    @Autowired
    private AsyncHttpClientService asyncHttpClientService;

    @GetMapping("/post/{id}")
    public Mono<String> getPost(@PathVariable("id") int id) {
        RequestHolder<String> request = new RequestHolder<>(HttpMethod.GET, "posts/" + id, String.class);
        return asyncHttpClientService.fetchAndParseAsync(request, null);
    }
}