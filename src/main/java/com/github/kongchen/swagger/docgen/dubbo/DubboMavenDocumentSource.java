package com.github.kongchen.swagger.docgen.dubbo;

import com.github.kongchen.swagger.docgen.AbstractDocumentSource;
import com.github.kongchen.swagger.docgen.mavenplugin.ApiSource;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

import java.util.HashSet;
import java.util.Set;

/**
 *
 */
public class DubboMavenDocumentSource extends AbstractDocumentSource<DubboReader> {


    public DubboMavenDocumentSource(Log log, ApiSource apiSource, String encoding) throws MojoFailureException {
        super(log, apiSource, encoding);
    }

    /**
     * 只抽取接口并且接口名包含 provider的
     * @return
     */
    @Override
    protected Set<Class<?>> getValidClasses() {
        Set<Class<?>> result = new HashSet<>();
        for (String location : apiSource.getLocations()) {
            for (String typeName : new Reflections(location, new SubTypesScanner(false)).getAllTypes()) {
                if (typeName.endsWith("Provider")){
                    try {
                        result.add(Class.forName(typeName));
                    } catch (ClassNotFoundException e) {
                    }
                }
            }
        }
        return result;
    }

    @Override
    protected DubboReader createReader() {
        return new DubboReader(this.swagger, this.LOG);
    }
}
