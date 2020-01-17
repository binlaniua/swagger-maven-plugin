package com.github.kongchen.swagger.docgen.reader;

import com.github.kongchen.swagger.docgen.GenerateException;
import com.github.kongchen.swagger.docgen.doc.JavaDoc;
import com.github.kongchen.swagger.docgen.mavenplugin.ApiSource;
import com.github.kongchen.swagger.docgen.spring.SpringResource;
import com.github.kongchen.swagger.docgen.spring.SpringSwaggerExtension;
import com.github.kongchen.swagger.docgen.util.SpringUtils;
import io.swagger.annotations.*;
import io.swagger.converter.ModelConverters;
import io.swagger.jaxrs.ext.SwaggerExtension;
import io.swagger.jaxrs.ext.SwaggerExtensions;
import io.swagger.models.*;
import io.swagger.models.Tag;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.util.BaseReaderUtils;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.StringUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;
import static org.springframework.core.annotation.AnnotationUtils.findAnnotation;

public class SpringMvcApiReader extends AbstractReader implements ClassSwaggerReader {
    private static final ResponseContainerConverter RESPONSE_CONTAINER_CONVERTER = new ResponseContainerConverter();

    private final SpringExceptionHandlerReader exceptionHandlerReader;

    private final ApiSource apiSource;

    private String resourcePath;

    public SpringMvcApiReader(final ApiSource apiSource, final Swagger swagger, final Log log) {
        super(swagger, log);
        this.apiSource = apiSource;
        this.exceptionHandlerReader = new SpringExceptionHandlerReader(log);
    }

    @Override
    protected void updateExtensionChain() {
        final List<SwaggerExtension> extensions = new ArrayList<SwaggerExtension>();
        extensions.add(new SpringSwaggerExtension(this.LOG));
        SwaggerExtensions.setExtensions(extensions);
    }

    @Override
    public Swagger read(final Set<Class<?>> classes) throws GenerateException {
        //relate all methods to one base request mapping if multiple controllers exist for that mapping
        //get all methods from each controller & find their request mapping
        //create map - resource string (after first slash) as key, new SpringResource as value
        final Map<String, SpringResource> resourceMap = this.generateResourceMap(classes);
        this.exceptionHandlerReader.processExceptionHandlers(classes);
        for (final SpringResource resource : resourceMap.values()) {
            this.read(resource);
        }

        return this.swagger;
    }

    public Swagger read(final SpringResource resource) {
        if (this.swagger == null) {
            this.swagger = new Swagger();
        }
        final List<Method> methods = resource.getMethods();
        Map<String, Tag> tags = new HashMap<String, Tag>();

        List<SecurityRequirement> resourceSecurities = new ArrayList<SecurityRequirement>();

        // Add the description from the controller api
        final Class<?> controller = resource.getControllerClass();
        final RequestMapping controllerRM = findMergedAnnotation(controller, RequestMapping.class);
        if (!this.isValidRequestMapping(controllerRM)) {
            return this.swagger;
        }

        String[] controllerProduces = new String[0];
        String[] controllerConsumes = new String[0];
        if (controllerRM != null) {
            controllerConsumes = controllerRM.consumes();
            controllerProduces = controllerRM.produces();
        }

        Api api = null;
        if (controller.isAnnotationPresent(Api.class)) {
            api = findMergedAnnotation(controller, Api.class);
            if (!this.canReadApi(false, api)) {
                return this.swagger;
            }
            tags = this.updateTagsForApi(null, api);
            resourceSecurities = this.getSecurityRequirements(api);
        }

        this.resourcePath = resource.getControllerMapping();

        //collect api from method with @RequestMapping
        final Map<String, List<Method>> apiMethodMap = this.collectApisByRequestMapping(methods);

        for (final String path : apiMethodMap.keySet()) {
            for (final Method method : apiMethodMap.get(path)) {
                final RequestMapping requestMapping = findMergedAnnotation(method, RequestMapping.class);
                if (requestMapping == null) {
                    continue;
                }
                if (!this.isValidRequestMapping(requestMapping)) {
                    continue;
                }
                final ApiOperation apiOperation = findMergedAnnotation(method, ApiOperation.class);
                if (apiOperation != null && apiOperation.hidden()) {
                    continue;
                }

                final Map<String, String> regexMap = new HashMap<String, String>();
                final String operationPath = this.parseOperationPath(path, regexMap);
                final RequestMethod[] requestMethods = this.getRequestMethod(requestMapping.method(), method, true);

                //http method
                for (final RequestMethod requestMethod : requestMethods) {
                    final String httpMethod = requestMethod.toString()
                                                           .toLowerCase();
                    final Operation operation = this.parseMethod(method, requestMethod);

                    this.updateOperationParameters(new ArrayList<Parameter>(), regexMap, operation);

                    this.updateOperationProtocols(apiOperation, operation);

                    String[] apiProduces = requestMapping.produces();
                    String[] apiConsumes = requestMapping.consumes();

                    apiProduces = (apiProduces.length == 0) ? controllerProduces : apiProduces;
                    apiConsumes = (apiConsumes.length == 0) ? controllerConsumes : apiConsumes;

                    apiConsumes = this.updateOperationConsumes(new String[0], apiConsumes, operation);
                    apiProduces = this.updateOperationProduces(new String[0], apiProduces, operation);

                    this.updateTagsForOperation(operation, apiOperation);
                    this.updateOperation(apiConsumes, apiProduces, tags, resourceSecurities, operation);
                    this.updatePath(operationPath, httpMethod, operation);
                }
            }
        }
        return this.swagger;
    }

    private boolean isValidRequestMapping(final RequestMapping requestMapping) {
        if (requestMapping != null && requestMapping.value() != null && requestMapping.value().length > 0) {
            return !requestMapping.value()[0].startsWith("$");
        }
        return true;
    }

    private RequestMethod[] getRequestMethod(RequestMethod[] origin, final Method method, final boolean buildPharse) {
        // 如果没有手动指定method
        if (origin.length == 0) {
            // 没有入参
            if (method.getParameterCount() == 0) {
                origin = new RequestMethod[]{RequestMethod.GET};
            }
            // 如果入参有RequestBody, 那么使用post
            else if (this.parameterHasAnnotation(method.getParameterAnnotations(), RequestBody.class)) {
                origin = new RequestMethod[]{RequestMethod.POST};
            }
            // 直接使用默认配置项目里面的method
            else if (StringUtils.isNotBlank(this.apiSource.getDefaultRequestMethod())) {
                origin = new RequestMethod[]{RequestMethod.valueOf(this.apiSource.getDefaultRequestMethod())};
            }
            if (buildPharse) {
                this.LOG.warn(String.format("方法[ %s ]设置了RequestMapping但是没有设置method, 动态判断添加结果 => [ %s ]", method.getName(), origin));
            }
        }
        return origin;
    }

    /**
     * 参数注解是否含有特定注解
     *
     * @param annss
     * @param target
     * @return
     */
    private boolean parameterHasAnnotation(final Annotation[][] annss, final Class target) {
        for (final Annotation[] anns : annss) {
            for (final Annotation ann : anns) {
                if (ann.annotationType() == target) {
                    return true;
                }
            }
        }
        return false;
    }

    private Operation parseMethod(final Method method, final RequestMethod requestMethod) {
        int responseCode = 200;
        final Operation operation = new Operation();

        final RequestMapping requestMapping = findMergedAnnotation(method, RequestMapping.class);
        Type responseClass = null;
        final List<String> produces = new ArrayList<String>();
        final List<String> consumes = new ArrayList<String>();
        String responseContainer = null;
        String operationId = this.getOperationId(method, requestMethod.name());
        Map<String, Property> defaultResponseHeaders = null;

        final ApiOperation apiOperation = findMergedAnnotation(method, ApiOperation.class);

        if (apiOperation != null) {
            if (apiOperation.hidden()) {
                return null;
            }
            if (!apiOperation.nickname()
                             .isEmpty()) {
                operationId = apiOperation.nickname();
            }

            defaultResponseHeaders = this.parseResponseHeaders(apiOperation.responseHeaders());

            operation.summary(apiOperation.value())
                     .description(apiOperation.notes());

            final Map<String, Object> customExtensions = BaseReaderUtils.parseExtensions(apiOperation.extensions());
            operation.setVendorExtensions(customExtensions);

            if (!apiOperation.response()
                             .equals(Void.class)) {
                responseClass = apiOperation.response();
            }
            if (!apiOperation.responseContainer()
                             .isEmpty()) {
                responseContainer = apiOperation.responseContainer();
            }

            ///security
            final List<SecurityRequirement> securities = new ArrayList<SecurityRequirement>();
            for (final Authorization auth : apiOperation.authorizations()) {
                if (!auth.value()
                         .isEmpty()) {
                    final SecurityRequirement security = new SecurityRequirement();
                    security.setName(auth.value());
                    for (final AuthorizationScope scope : auth.scopes()) {
                        if (!scope.scope()
                                  .isEmpty()) {
                            security.addScope(scope.scope());
                        }
                    }
                    securities.add(security);
                }
            }
            for (final SecurityRequirement sec : securities) {
                operation.security(sec);
            }

            responseCode = apiOperation.code();
        }
        // 用requestMapping的name
        else if (StringUtils.isNotEmpty(requestMapping.name())) {
            operation.setSummary(requestMapping.name());
        }
        // 如果没有定义ApiOperation
        else {
            operation.setSummary(JavaDoc.getInstance()
                                        .getMethodName(method));
        }

        if (responseClass == null) {
            // pick out response from method declaration
            this.LOG.info("picking up response class from method " + method);
            responseClass = method.getGenericReturnType();
        }
        if (responseClass instanceof ParameterizedType && ResponseEntity.class.equals(((ParameterizedType) responseClass).getRawType())) {
            responseClass = ((ParameterizedType) responseClass).getActualTypeArguments()[0];
        }
        boolean hasApiAnnotation = false;
        if (responseClass instanceof Class) {
            hasApiAnnotation = findAnnotation((Class) responseClass, Api.class) != null;
        }
        if (responseClass != null
                && !responseClass.equals(Void.class)
                && !responseClass.equals(ResponseEntity.class)
                && !hasApiAnnotation) {
            if (this.isPrimitive(responseClass)) {
                final Property property = ModelConverters.getInstance()
                                                         .readAsProperty(responseClass);
                if (property != null) {
                    final Property responseProperty = RESPONSE_CONTAINER_CONVERTER.withResponseContainer(responseContainer, property);
                    operation.response(responseCode, new Response()
                            .description("successful operation")
                            .schema(responseProperty)
                            .headers(defaultResponseHeaders));
                }
            } else if (!responseClass.equals(Void.class) && !responseClass.equals(void.class)) {
                final Map<String, Model> models = ModelConverters.getInstance()
                                                                 .read(responseClass);
                if (models.isEmpty()) {
                    final Property pp = ModelConverters.getInstance()
                                                       .readAsProperty(responseClass);
                    operation.response(responseCode, new Response()
                            .description("successful operation")
                            .schema(pp)
                            .headers(defaultResponseHeaders));
                }
                for (final String key : models.keySet()) {
                    final Property responseProperty = RESPONSE_CONTAINER_CONVERTER.withResponseContainer(responseContainer, new RefProperty().asDefault(key));
                    operation.response(responseCode, new Response()
                            .description("successful operation")
                            .schema(responseProperty)
                            .headers(defaultResponseHeaders));
                    this.swagger.model(key, models.get(key));
                }
            }
            final Map<String, Model> models = ModelConverters.getInstance()
                                                             .readAll(responseClass);
            for (final Map.Entry<String, Model> entry : models.entrySet()) {
                this.swagger.model(entry.getKey(), entry.getValue());
            }
        }

        operation.operationId(operationId);

        for (final String str : requestMapping.produces()) {
            if (!produces.contains(str)) {
                produces.add(str);
            }
        }
        for (final String str : requestMapping.consumes()) {
            if (!consumes.contains(str)) {
                consumes.add(str);
            }
        }

        final ApiResponses responseAnnotation = findMergedAnnotation(method, ApiResponses.class);
        if (responseAnnotation != null) {
            this.updateApiResponse(operation, responseAnnotation);
        } else {
            final ResponseStatus responseStatus = findMergedAnnotation(method, ResponseStatus.class);
            if (responseStatus != null) {
                this.updateResponseStatus(operation, responseStatus);
            }
        }

        final List<ResponseStatus> errorResponses = this.exceptionHandlerReader.getResponseStatusesFromExceptions(method);
        for (final ResponseStatus responseStatus : errorResponses) {
            final int code = responseStatus.code()
                                           .value();
            final String description = defaultIfEmpty(responseStatus.reason(), responseStatus.code()
                                                                                             .getReasonPhrase());
            operation.response(code, new Response().description(description));
        }

        this.overrideResponseMessages(operation);

        final Deprecated annotation = findAnnotation(method, Deprecated.class);
        if (annotation != null) {
            operation.deprecated(true);
        }

        // process parameters
        final Class[] parameterTypes = method.getParameterTypes();
        final Type[] genericParameterTypes = method.getGenericParameterTypes();
        final Annotation[][] paramAnnotations = method.getParameterAnnotations();
        final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
        final String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        // paramTypes = method.getParameterTypes
        // genericParamTypes = method.getGenericParameterTypes
        for (int i = 0; i < parameterTypes.length; i++) {
            final Type type = genericParameterTypes[i];
            final List<Annotation> annotations = Arrays.asList(paramAnnotations[i]);
            final List<Parameter> parameters = this.getParameters(type, annotations);


            for (final Parameter parameter : parameters) {
                if (parameter.getName()
                             .isEmpty()) {
                    parameter.setName(parameterNames[i]);
                }
                operation.parameter(parameter);
            }
        }

        if (operation.getResponses() == null) {
            operation.defaultResponse(new Response().description("successful operation"));
        }

        // Process @ApiImplicitParams
        this.readImplicitParameters(method, operation);

        this.processOperationDecorator(operation, method);

        return operation;
    }

    private void updateResponseStatus(final Operation operation, final ResponseStatus responseStatus) {
        final int code = responseStatus.value()
                                       .value();
        final String reason = responseStatus.reason();

        if (operation.getResponses() != null && operation.getResponses()
                                                         .size() == 1) {
            final String currentKey = operation.getResponses()
                                               .keySet()
                                               .iterator()
                                               .next();
            final Response oldResponse = operation.getResponses()
                                                  .remove(currentKey);
            if (StringUtils.isNotEmpty(reason)) {
                oldResponse.setDescription(reason);
            }
            operation.response(code, oldResponse);
        } else {
            operation.response(code, new Response().description(reason));
        }
    }

    private Map<String, List<Method>> collectApisByRequestMapping(final List<Method> methods) {
        final Map<String, List<Method>> apiMethodMap = new HashMap<String, List<Method>>();
        for (final Method method : methods) {
            final RequestMapping requestMapping = findMergedAnnotation(method, RequestMapping.class);
            if (requestMapping != null) {
                final String path;
                if (requestMapping.value().length != 0) {
                    path = this.generateFullPath(requestMapping.value()[0]);
                } else {
                    path = this.resourcePath;
                }
                if (apiMethodMap.containsKey(path)) {
                    apiMethodMap.get(path)
                                .add(method);
                } else {
                    final List<Method> ms = new ArrayList<Method>();
                    ms.add(method);
                    apiMethodMap.put(path, ms);
                }
            }
        }

        return apiMethodMap;
    }

    private String generateFullPath(final String path) {
        if (StringUtils.isNotEmpty(path)) {
            return this.resourcePath + (path.startsWith("/") ? path : '/' + path);
        } else {
            return this.resourcePath;
        }
    }

    //Helper method for loadDocuments()
    private Map<String, SpringResource> analyzeController(final Class<?> controllerClazz, final Map<String, SpringResource> resourceMap, final String description) {
        final String[] controllerRequestMappingValues = SpringUtils.getControllerResquestMapping(controllerClazz);

        // Iterate over all value attributes of the class-level RequestMapping annotation
        for (final String controllerRequestMappingValue : controllerRequestMappingValues) {
            ReflectionUtils.doWithMethods(controllerClazz, new ReflectionUtils.MethodCallback() {
                @Override
                public void doWith(final Method method) throws IllegalArgumentException, IllegalAccessException {
                    // Skip methods introduced by compiler
                    if (method.isSynthetic()) {
                        return;
                    }
                    final RequestMapping methodRequestMapping = findMergedAnnotation(method, RequestMapping.class);

                    // Look for method-level @RequestMapping annotation
                    if (methodRequestMapping != null) {
                        final RequestMethod[] requestMappingRequestMethods = SpringMvcApiReader.this.getRequestMethod(methodRequestMapping.method(), method, false);

                        // For each method-level @RequestMapping annotation, iterate over HTTP Verb
                        for (final RequestMethod requestMappingRequestMethod : requestMappingRequestMethods) {
                            final String[] methodRequestMappingValues = methodRequestMapping.value();

                            // Check for cases where method-level @RequestMapping#value is not set, and use the controllers @RequestMapping
                            if (methodRequestMappingValues.length == 0) {
                                // The map key is a concat of the following:
                                //   1. The controller package
                                //   2. The controller class name
                                //   3. The controller-level @RequestMapping#value
                                final String resourceKey = controllerClazz.getCanonicalName() + controllerRequestMappingValue + requestMappingRequestMethod;
                                if (!resourceMap.containsKey(resourceKey)) {
                                    resourceMap.put(
                                            resourceKey,
                                            new SpringResource(controllerClazz, controllerRequestMappingValue, resourceKey, description));
                                }
                                resourceMap.get(resourceKey)
                                           .addMethod(method);
                            } else {
                                // Here we know that method-level @RequestMapping#value is populated, so
                                // iterate over all the @RequestMapping#value attributes, and add them to the resource map.
                                for (final String methodRequestMappingValue : methodRequestMappingValues) {
                                    final String resourceKey = controllerClazz.getCanonicalName() + controllerRequestMappingValue
                                            + methodRequestMappingValue + requestMappingRequestMethod;
                                    if (!(controllerRequestMappingValue + methodRequestMappingValue).isEmpty()) {
                                        if (!resourceMap.containsKey(resourceKey)) {
                                            resourceMap.put(resourceKey, new SpringResource(controllerClazz, methodRequestMappingValue, resourceKey, description));
                                        }
                                        resourceMap.get(resourceKey)
                                                   .addMethod(method);
                                    }
                                }
                            }
                        }
                    }
                }
            });
        }
        controllerClazz.getFields();
        controllerClazz.getDeclaredFields(); //<--In case developer declares a field without an associated getter/setter.
        //this will allow NoClassDefFoundError to be caught before it triggers bamboo failure.

        return resourceMap;
    }

    protected Map<String, SpringResource> generateResourceMap(final Set<Class<?>> validClasses) throws GenerateException {
        Map<String, SpringResource> resourceMap = new HashMap<String, SpringResource>();
        for (final Class<?> aClass : validClasses) {
            final RequestMapping requestMapping = findAnnotation(aClass, RequestMapping.class);
            //This try/catch block is to stop a bamboo build from failing due to NoClassDefFoundError
            //This occurs when a class or method loaded by reflections contains a type that has no dependency
            try {
                resourceMap = this.analyzeController(aClass, resourceMap, "");
                final List<Method> mList = new ArrayList<Method>(Arrays.asList(aClass.getMethods()));
                if (aClass.getSuperclass() != null) {
                    mList.addAll(Arrays.asList(aClass.getSuperclass()
                                                     .getMethods()));
                }
            } catch (final NoClassDefFoundError e) {
                this.LOG.error(e.getMessage());
                this.LOG.info(aClass.getName());
                //exception occurs when a method type or annotation is not recognized by the plugin
            }
        }

        return resourceMap;
    }
}
