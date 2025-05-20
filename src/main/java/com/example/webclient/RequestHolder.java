package com.example.webclient;

import org.springframework.http.HttpMethod;

public class RequestHolder<T> {
    private final HttpMethod httpVerb;
    private final String uri;
    private final Class<T> jaxbClass;

    public RequestHolder(HttpMethod httpVerb, String uri, Class<T> jaxbClass) {
        this.httpVerb = httpVerb;
        this.uri = uri;
        this.jaxbClass = jaxbClass;
    }

    public HttpMethod getHttpVerb() {
        return httpVerb;
    }

    public String getUri() {
        return uri;
    }

    public Class<T> getJaxbClass() {
        return jaxbClass;
    }
}
