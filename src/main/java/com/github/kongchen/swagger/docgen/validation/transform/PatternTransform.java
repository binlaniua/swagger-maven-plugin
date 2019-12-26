package com.github.kongchen.swagger.docgen.validation.transform;

import javax.validation.constraints.Pattern;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class PatternTransform extends ValidationTransform<Pattern> {

    @Override
    public Map transform(Pattern a, Field field, Class clazz) {
        Map map = new HashMap();
        map.put("pattern", a.regexp());
        map.put("message", a.message());
        return map;
    }
}
