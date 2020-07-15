package com.github.kongchen.swagger.docgen.dubbo;

import com.github.kongchen.swagger.docgen.doc.JavaDoc;
import com.github.kongchen.swagger.docgen.util.TypeUtils;
import com.google.common.collect.Lists;
import io.swagger.converter.ModelConverters;
import io.swagger.jaxrs.ext.SwaggerExtension;
import io.swagger.jaxrs.ext.SwaggerExtensions;
import io.swagger.models.*;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.util.ParameterProcessor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.util.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

/**
 *
 */
@Setter
@Getter
@AllArgsConstructor
public class DubboMethod {

    /**
     *
     */
    private DubboResource dubboResource;

    /**
     *
     */
    private Method method;


    /**
     *
     */
    private Swagger swagger;

    /**
     */
    public void write() {
        //1. path
        String path = this.toPath();

        //2.
        final Operation operation = this.toOperation();

        //3. 插入到swagger
        Path sp = new Path();
        sp.set("post", operation);
        swagger.path(path, sp);
    }

    /**
     * @return
     */
    private String toPath() {
        return this.dubboResource.getName() + "/" + this.method.getName();
    }

    /**
     * @return
     */
    private Operation toOperation() {
        Operation operation = new Operation();

        //
        operation.setSummary(JavaDoc.getInstance().getMethodName(this.method));
        operation.setOperationId(this.toPath());

        // 挂在class下
        operation.addTag(this.dubboResource.getName());

        // 设置请求参数
        for (Parameter parameter : this.toParameters()) {
            operation.addParameter(parameter);
        }

        // 设置响应
        final Response response = this.toResponse();
        if (response != null) {
            operation.defaultResponse(response);
        }


        return operation;
    }

    /**
     * 生成请求对象
     *
     * @return
     */
    private List<Parameter> toParameters() {
        final Class[] parameterTypes = method.getParameterTypes();
        final Type[] genericParameterTypes = method.getGenericParameterTypes();
        final Annotation[][] paramAnnotations = method.getParameterAnnotations();
        final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
        final String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        // paramTypes = method.getParameterTypes
        // genericParamTypes = method.getGenericParameterTypes
        List<Parameter> result = new ArrayList<>();
        for (int i = 0; i < parameterTypes.length; i++) {
            final Type type = genericParameterTypes[i];
            final List<Annotation> annotations = Arrays.asList(paramAnnotations[i]);
            final List<Parameter> parameters = this.getParameters(type, annotations, parameterNames);
            result.addAll(parameters);
        }
        return result;
    }

    protected List<Parameter> getParameters(final Type type, final List<Annotation> annotations, String[] parameterNames) {
        final Iterator<SwaggerExtension> chain = SwaggerExtensions.chain();
        List<Parameter> parameters = new ArrayList<>();

        if (chain.hasNext()) {
            final SwaggerExtension extension = chain.next();
            parameters = extension.extractParameters(annotations, type, Collections.emptySet(), chain);
        }

        //
        if (!parameters.isEmpty()) {
            for (final Parameter parameter : parameters) {
                ParameterProcessor.applyAnnotations(this.swagger, parameter, type, annotations);
            }
        }
        //
        else {
            parameters = Lists.newArrayList();
            final Parameter param = ParameterProcessor.applyAnnotations(this.swagger, null, type, annotations);
            if (param != null) {
                parameters.add(param);
            }
        }

        for (int i = 0; i < parameters.size(); i++) {
            final Parameter parameter = parameters.get(i);
            if (StringUtils.isBlank(parameter.getName())) {
                parameter.setName(parameterNames[i]);
            }
            if (parameter instanceof BodyParameter){
                this.writeToComment(type, (BodyParameter) parameter);
            }
        }

        return parameters;
    }

    /**
     * @return
     */
    private Response toResponse() {
        final Type returnType = this.method.getGenericReturnType();
        if (returnType.equals(Void.class)) {
            return null;
        }

        Property property = null;


        // 如果是基础类型
        if (TypeUtils.isPrimitive(returnType)) {
            property = ModelConverters.getInstance()
                                      .readAsProperty(returnType);
        }

        // 非基础类型
        else {
            final Map<String, Model> models = ModelConverters.getInstance()
                                                             .read(returnType);
            if (models.isEmpty()) {
                property = ModelConverters.getInstance()
                                          .readAsProperty(returnType);
            } else {
                for (final String key : models.keySet()) {
                    final Model model = models.get(key);
                    property = new RefProperty().asDefault(key);
                    this.swagger.model(key, model);
                    this.writeToComment(returnType, model);
                }
            }
        }
        return new Response()
                .description("by qianmi")
                .schema(property);
    }


    private void writeToComment(Type type, BodyParameter parameter) {
        Model schema = parameter.getSchema();
        if (schema == null) {
            return;
        }
        if (StringUtils.isBlank(schema.getReference())) {
            return;
        }
        RefModel refModel = (RefModel) schema;
        ModelImpl model = (ModelImpl) swagger.getDefinitions()
                                             .get(refModel.getOriginalRef());
        this.writeToComment(type, model);
    }

    private void writeToComment(Type returnType, Model model){
        Map<String, Property> properties = model.getProperties();
        if (properties == null) {
            return;
        }

        //
        Map<String, List> rules = new HashMap<>();
        for (String pName : properties.keySet()) {

            // 反射这个字段
            Field field = ReflectionUtils.findField((Class<?>) returnType, pName);
            if (field == null) {
                continue;
            }

            Property property = properties.get(pName);
            if (StringUtils.isBlank(property.getDescription())){
                property.setDescription(JavaDoc.getInstance().getFieldName(field)); //设置注释
            }
        }
    }
}
