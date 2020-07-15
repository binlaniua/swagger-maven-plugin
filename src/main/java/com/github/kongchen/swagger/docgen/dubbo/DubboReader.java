package com.github.kongchen.swagger.docgen.dubbo;

import com.github.kongchen.swagger.docgen.GenerateException;
import com.github.kongchen.swagger.docgen.reader.AbstractReader;
import com.github.kongchen.swagger.docgen.reader.ClassSwaggerReader;
import io.swagger.models.Swagger;
import org.apache.maven.plugin.logging.Log;

import java.util.Set;

/**
 *
 */
public class DubboReader extends AbstractReader implements ClassSwaggerReader {

    public DubboReader(Swagger swagger, Log LOG) {
        super(swagger, LOG);
    }

    /**
     * @param classes
     * @return
     * @throws GenerateException
     */
    @Override
    public Swagger read(Set<Class<?>> classes) throws GenerateException {
        for (Class<?> aClass : classes) {
            final DubboResource dubboResource = new DubboResource(aClass);
            dubboResource.write(this.swagger);
        }
        return this.swagger;
    }
}
