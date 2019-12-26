package com.github.kongchen.swagger.docgen.validation;

import com.github.kongchen.swagger.docgen.validation.transform.*;
import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.RefModel;
import io.swagger.models.Swagger;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.properties.Property;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.Range;
import org.springframework.util.ReflectionUtils;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimpleValidationExtractor implements ValidationExtractor {

    private static final Map<Class, ValidationTransform> transformMap = new HashMap<>();

    static {
        transformMap.put(NotBlank.class, new NotBlankTransform());
        transformMap.put(NotNull.class, new NotNullTransform());
        transformMap.put(Pattern.class, new PatternTransform());
        transformMap.put(Size.class, new SizeTransform());
        transformMap.put(Range.class, new HibernateRangeTransform());
        transformMap.put(Length.class, new HibernateLengthTransform());
        transformMap.put(org.hibernate.validator.constraints.NotBlank.class, new HibernateNotBlankTransform());
    }

    @Override
    public void extract(Swagger swagger, Class paramType, BodyParameter bodyParameter) {
        Model schema = bodyParameter.getSchema();
        if (schema == null) {
            return;
        }
        if (StringUtils.isBlank(schema.getReference())) {
            return;
        }
        RefModel refModel = (RefModel) schema;
        ModelImpl model = (ModelImpl) swagger.getDefinitions()
                                             .get(refModel.getOriginalRef());
        Map<String, Property> properties = model.getProperties();
        if (properties == null) {
            return;
        }

        //
        Map<String, List> rules = new HashMap<>();
        for (String pName : properties.keySet()) {

            // 反射这个字段
            Field field = ReflectionUtils.findField(paramType, pName);
            if (field == null) {
                continue;
            }

            // 是否是否有特定验证注解
            List<Map> fieldRules = new ArrayList<>();
            for (Class aClass : transformMap.keySet()) {
                Annotation annotation = field.getAnnotation(aClass);
                if (annotation != null) {
                    Map map = transformMap.get(aClass)
                                          .transform(annotation, field, paramType);
                    if (map != null) {
                        fieldRules.add(map);
                    }
                }
            }

            //
            if (fieldRules.isEmpty()) {
                continue;
            }
            rules.put(pName, fieldRules);
        }

        //
        if (rules.isEmpty()) {
            return;
        }
        model.setVendorExtension("x-rules", rules);
    }


}
