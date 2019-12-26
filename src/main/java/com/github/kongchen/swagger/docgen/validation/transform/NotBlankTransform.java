package com.github.kongchen.swagger.docgen.validation.transform;

import javax.validation.constraints.NotBlank;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class NotBlankTransform extends ValidationTransform<NotBlank> {

    @Override
    public Map transform(NotBlank a, Field field, Class clazz) {
        Map map = new HashMap();
        map.put("required", true);
        map.put("message", a.message());
        return map;
    }
}
