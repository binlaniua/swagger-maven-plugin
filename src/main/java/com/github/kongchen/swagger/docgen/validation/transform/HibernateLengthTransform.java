package com.github.kongchen.swagger.docgen.validation.transform;

import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.NotBlank;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class HibernateLengthTransform extends ValidationTransform<Length> {

    @Override
    public Map transform(Length a, Field field, Class clazz) {
        Map map = new HashMap();
        if (a.min() != 0) {
            map.put("min", a.min());
        }
        if (a.max() != Integer.MAX_VALUE) {
            map.put("max", a.max());
        }
        map.put("message", a.message());
        return map;
    }
}
