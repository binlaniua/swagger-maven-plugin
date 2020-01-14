package com.github.kongchen.swagger.docgen.mavenplugin;

import com.github.kongchen.swagger.docgen.AbstractDocumentSource;
import com.github.kongchen.swagger.docgen.GenerateException;
import com.github.kongchen.swagger.docgen.doc.JavaDoc;
import io.swagger.models.Info;
import io.swagger.util.Json;

import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import java.io.*;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * User: kongchen
 * Date: 3/7/13
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.COMPILE, configurator = "include-project-dependencies",
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class ApiDocumentMojo extends AbstractMojo {

    /**
     * A set of apiSources.
     * One apiSource can be considered as a set of APIs for one apiVersion in a basePath
     */
    @Parameter
    private List<ApiSource> apiSources;

    /**
     * A set of feature enums which should be enabled on the JSON object mapper
     */
    @Parameter
    private List<String> enabledObjectMapperFeatures;

    /**
     *
     */
    @Parameter
    private List<String> callbacks;

    /**
     * A set of feature enums which should be enabled on the JSON object mapper
     */
    @Parameter
    private List<String> disabledObjectMapperFeatures;


    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    private String projectEncoding;

    @Component
    private MavenProjectHelper projectHelper;

    /**
     * A flag indicating if the generation should be skipped.
     */
    @Parameter(property = "swagger.skip", defaultValue = "false")
    private boolean skipSwaggerGeneration;

    @Parameter(property = "file.encoding")
    private String encoding;

    public List<ApiSource> getApiSources() {
        return apiSources;
    }

    public void setApiSources(List<ApiSource> apiSources) {
        this.apiSources = apiSources;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (project != null) {
            projectEncoding = project.getProperties()
                                     .getProperty("project.build.sourceEncoding");
        }

        if (skipSwaggerGeneration) {
            getLog().info("Swagger generation is skipped.");
            return;
        }

        if (apiSources == null) {
            throw new MojoFailureException("You must configure at least one apiSources element");
        }

        // 自动设置info进apiSource
        Model model = project.getParent()
                             .getModel();
        Info info = new Info();
        info.setTitle(project.getArtifactId());
        info.setVersion(model.getVersion());
        ApiSource source = apiSources.get(0);
        source.setSpringmvc(true); //肯定是mvc
        source.setInfo(info);
        source.setOutputFormats("json");
        source.setSwaggerDirectory("../swagger");

        // 增加java doc
        JavaDoc.getInstance()
               .init(project);


        if (useSwaggerSpec11()) {
            throw new MojoExecutionException("You may use an old version of swagger which is not supported by swagger-maven-plugin 2.0+\n" +
                    "swagger-maven-plugin 2.0+ only supports swagger-core 1.3.x");
        }

        if (useSwaggerSpec13()) {
            throw new MojoExecutionException("You may use an old version of swagger which is not supported by swagger-maven-plugin 3.0+\n" +
                    "swagger-maven-plugin 3.0+ only supports swagger spec 2.0");
        }

        try {
            getLog().debug(apiSources.toString());

            if (enabledObjectMapperFeatures != null) {
                configureObjectMapperFeatures(enabledObjectMapperFeatures, true);

            }

            if (disabledObjectMapperFeatures != null) {
                configureObjectMapperFeatures(disabledObjectMapperFeatures, false);
            }

            for (ApiSource apiSource : apiSources) {
                validateConfiguration(apiSource);
                AbstractDocumentSource documentSource = apiSource.isSpringmvc()
                        ? new SpringMavenDocumentSource(apiSource, getLog(), projectEncoding)
                        : new MavenDocumentSource(apiSource, getLog(), projectEncoding);

                documentSource.loadTypesToSkip();
                documentSource.loadModelModifier();
                documentSource.loadModelConverters();
                documentSource.loadDocuments();

                createOutputDirs(apiSource.getOutputPath());

                if (apiSource.getTemplatePath() != null) {
                    documentSource.toDocuments();
                }
                String swaggerFileName = getSwaggerFileName(apiSource.getSwaggerFileName());
                documentSource.toSwaggerDocuments(
                        apiSource.getSwaggerUIDocBasePath() == null
                                ? apiSource.getBasePath()
                                : apiSource.getSwaggerUIDocBasePath(),
                        apiSource.getOutputFormats(), swaggerFileName, projectEncoding);

                if (apiSource.isAttachSwaggerArtifact() && apiSource.getSwaggerDirectory() != null && project != null) {
                    String outputFormats = apiSource.getOutputFormats();
                    if (outputFormats != null) {
                        for (String format : outputFormats.split(",")) {
                            String classifier = swaggerFileName.equals("swagger")
                                    ? getSwaggerDirectoryName(apiSource.getSwaggerDirectory())
                                    : swaggerFileName;
                            File swaggerFile = new File(apiSource.getSwaggerDirectory(), swaggerFileName + "." + format.toLowerCase());
                            projectHelper.attachArtifact(project, format.toLowerCase(), classifier, swaggerFile);
                            notifyCallback(swaggerFile);
                        }
                    }
                }
            }
        } catch (GenerateException e) {
            throw new MojoFailureException(e.getMessage(), e);
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void notifyCallback(File swaggerFile) {
        if (callbacks == null || callbacks.isEmpty()) {
            return;
        }
        try {
            String fileBody = FileUtils.readFileToString(swaggerFile, "UTF-8");
            for (String callback : callbacks) {
                URL httpUrl = new URL(callback);
                HttpURLConnection conn = (HttpURLConnection) httpUrl.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("connection", "close");
                conn.setUseCaches(false);//设置不要缓存
                conn.setInstanceFollowRedirects(true);
                conn.setDoOutput(true);
                conn.setDoInput(true);
                conn.connect();
                try (
                        OutputStreamWriter out = new OutputStreamWriter(conn.getOutputStream());
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))
                ) {
                    out.write(fileBody);
                    out.flush();
                    while ((reader.readLine()) != null) {
                    }
                }
                conn.disconnect();
            }
        } catch (IOException e) {
        }
    }

    private void createOutputDirs(String outputPath) throws MojoExecutionException {
        if (outputPath != null) {
            File outputDirectory = new File(outputPath).getParentFile();
            if (outputDirectory != null && !outputDirectory.exists()) {
                if (!outputDirectory.mkdirs()) {
                    throw new MojoExecutionException(
                            String.format("Create directory [%s] for output failed.", outputPath));
                }
            }
        }
    }

    /**
     * validate configuration according to swagger spec and plugin requirement
     *
     * @param apiSource
     * @throws GenerateException
     */
    private void validateConfiguration(ApiSource apiSource) throws GenerateException {
        if (apiSource == null) {
            throw new GenerateException("You do not configure any apiSource!");
        } else if (apiSource.getInfo() == null) {
            throw new GenerateException("`<info>` is required by Swagger Spec.");
        }
        if (apiSource.getInfo()
                     .getTitle() == null) {
            throw new GenerateException("`<info><title>` is required by Swagger Spec.");
        }

        if (apiSource.getInfo()
                     .getVersion() == null) {
            throw new GenerateException("`<info><version>` is required by Swagger Spec.");
        }

        if (apiSource.getInfo()
                     .getLicense() != null && apiSource.getInfo()
                                                       .getLicense()
                                                       .getName() == null) {
            throw new GenerateException("`<info><license><name>` is required by Swagger Spec.");
        }

        if (apiSource.getLocations() == null) {
            throw new GenerateException("<locations> is required by this plugin.");
        }

    }

    private boolean useSwaggerSpec11() {
        try {
            Class<?> tryClass = Class.forName("com.wordnik.swagger.annotations.ApiErrors");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean useSwaggerSpec13() {
        try {
            Class<?> tryClass = Class.forName("com.wordnik.swagger.model.ApiListing");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private String getSwaggerFileName(String swaggerFileName) {
        return swaggerFileName == null || "".equals(swaggerFileName.trim()) ? "swagger" : swaggerFileName;
    }

    private String getSwaggerDirectoryName(String swaggerDirectory) {
        return new File(swaggerDirectory).getName();
    }

    private void configureObjectMapperFeatures(List<String> features, boolean enabled) throws Exception {
        for (String feature : features) {
            int i = feature.lastIndexOf(".");
            Class clazz = Class.forName(feature.substring(0, i));
            Enum e = Enum.valueOf(clazz, feature.substring(i + 1));
            getLog().debug("enabling " + e.getDeclaringClass()
                                          .toString() + "." + e.name() + "");
            Method method = Json.mapper()
                                .getClass()
                                .getMethod("configure", e.getClass(), boolean.class);
            method.invoke(Json.mapper(), e, enabled);
        }
    }

}
