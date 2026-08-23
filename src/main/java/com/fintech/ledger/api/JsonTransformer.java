package com.fintech.ledger.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import spark.ResponseTransformer;

public class JsonTransformer implements ResponseTransformer {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    public String render(Object model) throws Exception {
        return MAPPER.writeValueAsString(model);
    }

    public static ObjectMapper getMapper() {
        return MAPPER;
    }
}