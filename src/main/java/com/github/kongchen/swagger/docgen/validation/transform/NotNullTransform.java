package com.github.kongchen.swagger.docgen.validation.transform;

import javax.validation.constraints.NotNull;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class NotNullTransform extends ValidationTransform<NotNull> {

    @Override
    public Map transform(NotNull a, Field field, Class clazz) {
        Map map = new HashMap();
        map.put("required", true);
        map.put("message", a.message());
        return map;
    }
}
