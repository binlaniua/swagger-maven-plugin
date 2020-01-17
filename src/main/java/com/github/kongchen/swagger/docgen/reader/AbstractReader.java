package com.github.kongchen.swagger.docgen.reader;

import com.github.kongchen.swagger.docgen.ResponseMessageOverride;
import com.github.kongchen.swagger.docgen.util.TypeExtracter;
import com.github.kongchen.swagger.docgen.util.TypeWithAnnotations;
import com.github.kongchen.swagger.docgen.validation.SimpleValidationExtractor;
import com.github.kongchen.swagger.docgen.validation.ValidationExtractor;
import com.google.common.collect.Lists;
import com.sun.jersey.api.core.InjectParam;
import io.swagger.annotations.*;
import io.swagger.converter.ModelConverters;
import io.swagger.jaxrs.ext.SwaggerExtension;
import io.swagger.jaxrs.ext.SwaggerExtensions;
import io.swagger.models.*;
import io.swagger.models.Path;
import io.swagger.models.Tag;
import io.swagger.models.parameters.*;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.MapProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.util.ParameterProcessor;
import io.swagger.util.PathUtils;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.apache.commons.lang3.text.StrBuilder;
import org.apache.maven.plugin.logging.Log;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.bind.annotation.*;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

import javax.ws.rs.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @author chekong on 15/4/28.
 */
public abstract class AbstractReader {
    private static final ResponseContainerConverter RESPONSE_CONTAINER_CONVERTER = new ResponseContainerConverter();
    protected final Log LOG;
    protected Swagger swagger;
    private Set<Type> typesToSkip = new HashSet<Type>();
    protected List<ResponseMessageOverride> responseMessageOverrides;

    protected String operationIdFormat;

    protected ValidationExtractor validationExtractor = new SimpleValidationExtractor();

    /**
     * Supported parameters: {{packageName}}, {{className}}, {{methodName}}, {{httpMethod}}
     * Suggested default value is: "{{className}}_{{methodName}}_{{httpMethod}}"
     */
    public static final String OPERATION_ID_FORMAT_DEFAULT = "{{methodName}}";

    public Set<Type> getTypesToSkip() {
        return this.typesToSkip;
    }

    public void setTypesToSkip(final List<Type> typesToSkip) {
        this.typesToSkip = new HashSet<Type>(typesToSkip);
    }

    public void setTypesToSkip(final Set<Type> typesToSkip) {
        this.typesToSkip = typesToSkip;
    }

    public void addTypeToSkippedTypes(final Type type) {
        this.typesToSkip.add(type);
    }

    public void setResponseMessageOverrides(final List<ResponseMessageOverride> responseMessageOverrides) {
        this.responseMessageOverrides = responseMessageOverrides;
    }

    public List<ResponseMessageOverride> getResponseMessageOverrides() {
        return this.responseMessageOverrides;
    }

    public AbstractReader(final Swagger swagger, final Log LOG) {
        this.swagger = swagger;
        this.LOG = LOG;
        this.updateExtensionChain();
    }

    /**
     * Method which allows sub-classes to modify the Swagger extension chain.
     */
    protected void updateExtensionChain() {
        // default implementation does nothing
    }

    protected List<SecurityRequirement> getSecurityRequirements(final Api api) {
        final List<SecurityRequirement> securities = new ArrayList<>();
        if (api == null) {
            return securities;
        }

        for (final Authorization auth : api.authorizations()) {
            if (auth.value()
                    .isEmpty()) {
                continue;
            }
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
        return securities;
    }

    protected String parseOperationPath(final String operationPath, final Map<String, String> regexMap) {
        return PathUtils.parsePath(operationPath, regexMap);
    }

    protected void updateOperationParameters(final List<Parameter> parentParameters, final Map<String, String> regexMap, final Operation operation) {
        if (parentParameters != null) {
            for (final Parameter param : parentParameters) {
                operation.parameter(param);
            }
        }
        for (final Parameter param : operation.getParameters()) {
            final String pattern = regexMap.get(param.getName());
            if (pattern != null) {
                param.setPattern(pattern);
            }
        }
    }

    protected void overrideResponseMessages(final Operation operation) {
        if (this.responseMessageOverrides != null) {
            for (final ResponseMessageOverride responseMessage : this.responseMessageOverrides) {
                operation.response(responseMessage.getCode(), this.createResponse(responseMessage));
            }
        }
    }

    private Response createResponse(final ResponseMessageOverride responseMessage) {
        final Response response = new Response()
                .description(responseMessage.getMessage());
        if (responseMessage.getExample() != null) {
            response.example(
                    responseMessage.getExample()
                                   .getMediaType(),
                    responseMessage.getExample()
                                   .getValue());
        }
        return response;
    }

    protected Map<String, Property> parseResponseHeaders(final ResponseHeader[] headers) {
        if (headers == null) {
            return null;
        }
        Map<String, Property> responseHeaders = null;
        for (final ResponseHeader header : headers) {
            if (header.name()
                      .isEmpty()) {
                continue;
            }
            if (responseHeaders == null) {
                responseHeaders = new HashMap<>();
            }
            final Class<?> cls = header.response();

            if (!cls.equals(Void.class) && !cls.equals(void.class)) {
                final Property property = ModelConverters.getInstance()
                                                         .readAsProperty(cls);
                if (property != null) {
                    final Property responseProperty;

                    if (header.responseContainer()
                              .equalsIgnoreCase("list")) {
                        responseProperty = new ArrayProperty(property);
                    } else if (header.responseContainer()
                                     .equalsIgnoreCase("map")) {
                        responseProperty = new MapProperty(property);
                    } else {
                        responseProperty = property;
                    }
                    responseProperty.setDescription(header.description());
                    responseHeaders.put(header.name(), responseProperty);
                }
            }
        }
        return responseHeaders;
    }

    protected void updatePath(final String operationPath, final String httpMethod, final Operation operation) {
        if (httpMethod == null) {
            return;
        }
        Path path = this.swagger.getPath(operationPath);
        if (path == null) {
            path = new Path();
            this.swagger.path(operationPath, path);
        }
        path.set(httpMethod, operation);
    }

    protected void updateTagsForOperation(final Operation operation, final ApiOperation apiOperation) {
        if (apiOperation == null) {
            return;
        }
        for (final String tag : apiOperation.tags()) {
            if (!tag.isEmpty()) {
                operation.tag(tag);
                this.swagger.tag(new Tag().name(tag));
            }
        }
    }

    protected boolean canReadApi(final boolean readHidden, final Api api) {
        return (api == null) || (readHidden) || (!api.hidden());
    }

    protected Set<Tag> extractTags(final Api api) {
        final Set<Tag> output = new LinkedHashSet<>();
        if (api == null) {
            return output;
        }

        boolean hasExplicitTags = false;
        for (final String tag : api.tags()) {
            if (!tag.isEmpty()) {
                hasExplicitTags = true;
                output.add(new Tag().name(tag));
            }
        }
        if (!hasExplicitTags) {
            // derive tag from api path + description
            final String tagString = api.value()
                                        .replace("/", "");
            if (!tagString.isEmpty()) {
                final Tag tag = new Tag().name(tagString);
                if (!api.description()
                        .isEmpty()) {
                    tag.description(api.description());
                }
                output.add(tag);
            }
        }
        return output;
    }

    protected void updateOperationProtocols(final ApiOperation apiOperation, final Operation operation) {
        if (apiOperation == null) {
            return;
        }
        final String[] protocols = apiOperation.protocols()
                                               .split(",");
        for (final String protocol : protocols) {
            final String trimmed = protocol.trim();
            if (!trimmed.isEmpty()) {
                operation.scheme(Scheme.forValue(trimmed));
            }
        }
    }

    protected Map<String, Tag> updateTagsForApi(final Map<String, Tag> parentTags, final Api api) {
        // the value will be used as a tag for 2.0 UNLESS a Tags annotation is present
        final Map<String, Tag> tagsMap = new HashMap<>();
        for (final Tag tag : this.extractTags(api)) {
            tagsMap.put(tag.getName(), tag);
        }
        if (parentTags != null) {
            tagsMap.putAll(parentTags);
        }
        for (final Tag tag : tagsMap.values()) {
            this.swagger.tag(tag);
        }
        return tagsMap;
    }

    protected boolean isPrimitive(final Type cls) {
        return com.github.kongchen.swagger.docgen.util.TypeUtils.isPrimitive(cls);
    }

    protected void updateOperation(final String[] apiConsumes, final String[] apiProduces, final Map<String, Tag> tags, final List<SecurityRequirement> securities, final Operation operation) {
        if (operation == null) {
            return;
        }
        if (operation.getConsumes() == null) {
            for (final String mediaType : apiConsumes) {
                operation.consumes(mediaType);
            }
        }
        if (operation.getProduces() == null) {
            for (final String mediaType : apiProduces) {
                operation.produces(mediaType);
            }
        }

        if (operation.getTags() == null) {
            for (final String tagString : tags.keySet()) {
                operation.tag(tagString);
            }
        }
        for (final SecurityRequirement security : securities) {
            operation.security(security);
        }
    }

    private boolean isApiParamHidden(final List<Annotation> parameterAnnotations) {
        for (final Annotation parameterAnnotation : parameterAnnotations) {
            if (parameterAnnotation instanceof ApiParam) {
                return ((ApiParam) parameterAnnotation).hidden();
            }
        }

        return false;
    }

    private boolean hasValidAnnotations(final List<Annotation> parameterAnnotations) {
        // Because method parameters can contain parameters that are valid, but
        // not part of the API contract, first check to make sure the parameter
        // has at lease one annotation before processing it.  Also, check a
        // whitelist to make sure that the annotation of the parameter is
        // compatible with spring-maven-plugin

        final List<Type> validParameterAnnotations = new ArrayList<>();
        validParameterAnnotations.add(ModelAttribute.class);
        validParameterAnnotations.add(BeanParam.class);
        validParameterAnnotations.add(InjectParam.class);
        validParameterAnnotations.add(ApiParam.class);
        validParameterAnnotations.add(PathParam.class);
        validParameterAnnotations.add(QueryParam.class);
        validParameterAnnotations.add(HeaderParam.class);
        validParameterAnnotations.add(FormParam.class);
        validParameterAnnotations.add(RequestParam.class);
        validParameterAnnotations.add(RequestBody.class);
        validParameterAnnotations.add(PathVariable.class);
        validParameterAnnotations.add(RequestHeader.class);
        validParameterAnnotations.add(RequestPart.class);
        validParameterAnnotations.add(CookieValue.class);


        boolean hasValidAnnotation = false;
        for (final Annotation potentialAnnotation : parameterAnnotations) {
            if (validParameterAnnotations.contains(potentialAnnotation.annotationType())) {
                hasValidAnnotation = true;
                break;
            }
        }

        return hasValidAnnotation;
    }

    // this is final to enforce that only the implementation method below can be overridden, to avoid confusion
    protected final List<Parameter> getParameters(final Type type, final List<Annotation> annotations) {
        return this.getParameters(type, annotations, this.typesToSkip);
    }

    // this method exists so that outside callers can choose their own custom types to skip
    protected List<Parameter> getParameters(final Type type, final List<Annotation> annotations, final Set<Type> typesToSkip) {
        // 开发不规范有的入参并没有加任何注解
//        if (!hasValidAnnotations(annotations) || isApiParamHidden(annotations)) {
//            return Collections.emptyList();
//        }
        if (this.isApiParamHidden(annotations)) {
            return Collections.emptyList();
        }


        final Iterator<SwaggerExtension> chain = SwaggerExtensions.chain();
        List<Parameter> parameters = new ArrayList<>();
        final Class<?> cls = TypeUtils.getRawType(type, type);
        this.LOG.debug("Looking for path/query/header/form/cookie params in " + cls);

        if (chain.hasNext()) {
            final SwaggerExtension extension = chain.next();
            this.LOG.debug("trying extension " + extension);
            parameters = extension.extractParameters(annotations, type, typesToSkip, chain);
        }

        if (!parameters.isEmpty()) {
            for (final Parameter parameter : parameters) {
                ParameterProcessor.applyAnnotations(this.swagger, parameter, type, annotations);
            }
        } else {
            this.LOG.debug("Looking for body params in " + cls);
            // parameters is guaranteed to be empty at this point, replace it with a mutable collection
            parameters = Lists.newArrayList();
            if (!typesToSkip.contains(type)) {
                final Parameter param = ParameterProcessor.applyAnnotations(this.swagger, null, type, annotations);
                if (param != null) {
                    parameters.add(param);
                }
                // 如果param是body, 那么扩展验证规则进入
                if (param != null && param instanceof BodyParameter) {
                    if (type instanceof ParameterizedTypeImpl) {
                        validationExtractor.extract(this.swagger, ((ParameterizedTypeImpl) type).getClass(), (BodyParameter) param);
                    } else {
                        validationExtractor.extract(this.swagger, (Class) type, (BodyParameter) param);
                    }

                }
            }
        }
        return parameters;
    }


    protected void updateApiResponse(final Operation operation, final ApiResponses responseAnnotation) {
        boolean contains200 = false;
        boolean contains2xx = false;
        for (final ApiResponse apiResponse : responseAnnotation.value()) {
            final Map<String, Property> responseHeaders = this.parseResponseHeaders(apiResponse.responseHeaders());
            final Class<?> responseClass = apiResponse.response();
            final Response response = new Response()
                    .description(apiResponse.message())
                    .headers(responseHeaders);

            if (responseClass.equals(Void.class)) {
                if (operation.getResponses() != null) {
                    final Response apiOperationResponse = operation.getResponses()
                                                                   .get(String.valueOf(apiResponse.code()));
                    if (apiOperationResponse != null) {
                        response.setSchema(apiOperationResponse.getSchema());
                    }
                }
            } else if (this.isPrimitive(responseClass)) {
                final Property property = ModelConverters.getInstance()
                                                         .readAsProperty(responseClass);
                if (property != null) {
                    response.setSchema(RESPONSE_CONTAINER_CONVERTER.withResponseContainer(apiResponse.responseContainer(), property));
                }
            } else {
                Map<String, Model> models = ModelConverters.getInstance()
                                                           .read(responseClass);
                for (final String key : models.keySet()) {
                    final Property schema = new RefProperty().asDefault(key);
                    response.setSchema(RESPONSE_CONTAINER_CONVERTER.withResponseContainer(apiResponse.responseContainer(), schema));
                    this.swagger.model(key, models.get(key));
                }
                models = ModelConverters.getInstance()
                                        .readAll(responseClass);
                for (final Map.Entry<String, Model> entry : models.entrySet()) {
                    this.swagger.model(entry.getKey(), entry.getValue());
                }

                if (response.getSchema() == null) {
                    final Map<String, Response> responses = operation.getResponses();
                    if (responses != null) {
                        final Response apiOperationResponse = responses.get(String.valueOf(apiResponse.code()));
                        if (apiOperationResponse != null) {
                            response.setSchema(apiOperationResponse.getSchema());
                        }
                    }
                }
            }

            if (apiResponse.code() == 0) {
                operation.defaultResponse(response);
            } else {
                operation.response(apiResponse.code(), response);
            }
            if (apiResponse.code() == 200) {
                contains200 = true;
            } else if (apiResponse.code() > 200 && apiResponse.code() < 300) {
                contains2xx = true;
            }
        }
        if (!contains200 && contains2xx) {
            final Map<String, Response> responses = operation.getResponses();
            //technically should not be possible at this point, added to be safe
            if (responses != null) {
                responses.remove("200");
            }
        }
    }

    protected String[] updateOperationProduces(final String[] parentProduces, String[] apiProduces, final Operation operation) {
        if (parentProduces != null) {
            final Set<String> both = new LinkedHashSet<>(Arrays.asList(apiProduces));
            both.addAll(Arrays.asList(parentProduces));
            if (operation.getProduces() != null) {
                both.addAll(operation.getProduces());
            }
            apiProduces = both.toArray(new String[both.size()]);
        }
        return apiProduces;
    }

    protected String[] updateOperationConsumes(final String[] parentConsumes, String[] apiConsumes, final Operation operation) {
        if (parentConsumes != null) {
            final Set<String> both = new LinkedHashSet<>(Arrays.asList(apiConsumes));
            both.addAll(Arrays.asList(parentConsumes));
            if (operation.getConsumes() != null) {
                both.addAll(operation.getConsumes());
            }
            apiConsumes = both.toArray(new String[both.size()]);
        }
        return apiConsumes;
    }

    protected void readImplicitParameters(final Method method, final Operation operation) {
        final ApiImplicitParams implicitParams = AnnotationUtils.findAnnotation(method, ApiImplicitParams.class);
        if (implicitParams == null) {
            return;
        }
        for (final ApiImplicitParam param : implicitParams.value()) {
            final Class<?> cls;
            try {
                cls = param.dataTypeClass() == Void.class ?
                        Class.forName(param.dataType()) :
                        param.dataTypeClass();
            } catch (final ClassNotFoundException e) {
                this.LOG.warn(String.format("定义了ApiImplicitParam, 但是找不到dataType[ %s ]", param.dataType()));
                return;
            }

            final Parameter p = this.readImplicitParam(param, cls);
            if (p != null) {
                if (p instanceof BodyParameter) {
                    final Iterator<Parameter> iterator = operation.getParameters()
                                                                  .iterator();
                    while (iterator.hasNext()) {
                        final Parameter parameter = iterator.next();
                        if (parameter instanceof BodyParameter) {
                            iterator.remove();
                        }
                    }
                }
                operation.addParameter(p);
            }
        }
    }

    protected Parameter readImplicitParam(final ApiImplicitParam param, final Class<?> apiClass) {
        final Parameter parameter;
        if (param.paramType()
                 .equalsIgnoreCase("path")) {
            parameter = new PathParameter();
        } else if (param.paramType()
                        .equalsIgnoreCase("query")) {
            parameter = new QueryParameter();
        } else if (param.paramType()
                        .equalsIgnoreCase("form") || param.paramType()
                                                          .equalsIgnoreCase("formData")) {
            parameter = new FormParameter();
        } else if (param.paramType()
                        .equalsIgnoreCase("body")) {
            parameter = new BodyParameter();
        } else if (param.paramType()
                        .equalsIgnoreCase("header")) {
            parameter = new HeaderParameter();
        } else {
            return null;
        }

        return ParameterProcessor.applyAnnotations(this.swagger, parameter, apiClass, Arrays.asList(new Annotation[]{param}));
    }

    void processOperationDecorator(final Operation operation, final Method method) {
        final Iterator<SwaggerExtension> chain = SwaggerExtensions.chain();
        if (chain.hasNext()) {
            final SwaggerExtension extension = chain.next();
            extension.decorateOperation(operation, method, chain);
        }
    }

    protected String getOperationId(final Method method, final String httpMethod) {
        if (this.operationIdFormat == null) {
            this.operationIdFormat = OPERATION_ID_FORMAT_DEFAULT;
        }

        final String packageName = method.getDeclaringClass()
                                         .getPackage()
                                         .getName();
        final String className = method.getDeclaringClass()
                                       .getSimpleName();
        final String methodName = method.getName();

        final StrBuilder sb = new StrBuilder(this.operationIdFormat);
        sb.replaceAll("{{packageName}}", packageName);
        sb.replaceAll("{{className}}", className);
        sb.replaceAll("{{methodName}}", methodName);
        sb.replaceAll("{{httpMethod}}", httpMethod);

        return sb.toString();
    }

    public List<Parameter> extractTypes(final Class<?> cls, final Set<Type> typesToSkip, final List<Annotation> additionalAnnotations) {
        final TypeExtracter extractor = new TypeExtracter();
        final Collection<TypeWithAnnotations> typesWithAnnotations = extractor.extractTypes(cls);

        final List<Parameter> output = new ArrayList<Parameter>();
        for (final TypeWithAnnotations typeWithAnnotations : typesWithAnnotations) {

            final Type type = typeWithAnnotations.getType();
            final List<Annotation> annotations = new ArrayList<Annotation>(additionalAnnotations);
            annotations.addAll(typeWithAnnotations.getAnnotations());

            /*
             * Skip the type of the bean itself when recursing into its members
             * in order to avoid a cycle (stack overflow), as crazy as that user
             * code would have to be.
             *
             * There are no tests to prove this works because the test bean
             * classes are shared with SwaggerReaderTest and Swagger's own logic
             * doesn't prevent this problem.
             */
            final Set<Type> recurseTypesToSkip = new HashSet<Type>(typesToSkip);
            recurseTypesToSkip.add(cls);

            output.addAll(this.getParameters(type, annotations, recurseTypesToSkip));
        }

        return output;
    }

    public String getOperationIdFormat() {
        return this.operationIdFormat;
    }

    public void setOperationIdFormat(final String operationIdFormat) {
        this.operationIdFormat = operationIdFormat;
    }
}

