package com.example.pwm.controller;

import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;

@Controller
public class GraphQLExceptionHandler {
    @GraphQlExceptionHandler(ResponseStatusException.class)
    public GraphQLError handle(ResponseStatusException ex) {
        return GraphqlErrorBuilder.newError()
                .errorType(ErrorType.BAD_REQUEST)
                .message(ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString())
                .build();
    }
}
