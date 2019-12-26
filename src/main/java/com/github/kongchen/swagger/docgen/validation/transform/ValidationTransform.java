package com.github.kongchen.swagger.docgen.validation.transform;

import java.lang.reflect.Field;
import java.util.Map;

public abstract class ValidationTransform<T> {

    public abstract Map transform(T a, Field field, Class clazz);
}
