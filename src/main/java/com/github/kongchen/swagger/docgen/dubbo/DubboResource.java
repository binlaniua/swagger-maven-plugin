package com.github.kongchen.swagger.docgen.dubbo;

import io.swagger.models.Swagger;
import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.Method;

/**
 *
 */
@Getter
@Setter
public class DubboResource {

    /**
     *
     */
    private Class<?> providerClass;

    public DubboResource(Class<?> providerClass) {
        this.providerClass = providerClass;
    }

    /**
     * 写入到swagger
     *
     * @param swagger
     */
    public void write(Swagger swagger) {
        for (Method method : this.providerClass.getMethods()) {
            final DubboMethod dubboMethod = new DubboMethod(this, method, swagger);
            dubboMethod.write();
        }
    }

    public String getName() {
        return this.providerClass.getSimpleName();
    }
}
