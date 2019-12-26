package com.github.kongchen.swagger.docgen.validation;

import io.swagger.models.Swagger;
import io.swagger.models.parameters.BodyParameter;

public interface ValidationExtractor {

    /**
     * @param clazz
     * @param bodyParameter
     */
    void extract(Swagger swagger, Class clazz, BodyParameter bodyParameter);
}
