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
import org.apache.maven.plugins.annotations.*;
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
        return this.apiSources;
    }

    public void setApiSources(final List<ApiSource> apiSources) {
        this.apiSources = apiSources;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (this.project != null) {
            this.projectEncoding = this.project.getProperties()
                                               .getProperty("project.build.sourceEncoding");
        }

        if (this.skipSwaggerGeneration) {
            this.getLog()
                .info("Swagger generation is skipped.");
            return;
        }

        if (this.apiSources == null) {
            throw new MojoFailureException("You must configure at least one apiSources element");
        }

        // 自动设置info进apiSource
        final Model model = this.project.getParent()
                                        .getModel();
        final Info info = new Info();
        info.setTitle(this.project.getArtifactId());
        info.setVersion(model.getVersion());
        info.setDescription(this.project.getGroupId() + "|" + this.project.getArtifactId());
        final ApiSource source = this.apiSources.get(0);
        source.setSpringmvc(true); //肯定是mvc
        source.setInfo(info);
        source.setOutputFormats("json");
        source.setSwaggerDirectory("./swagger");

        // 增加java doc
        JavaDoc.getInstance()
               .init(this.project);


        if (this.useSwaggerSpec11()) {
            throw new MojoExecutionException("You may use an old version of swagger which is not supported by swagger-maven-plugin 2.0+\n" +
                    "swagger-maven-plugin 2.0+ only supports swagger-core 1.3.x");
        }

        if (this.useSwaggerSpec13()) {
            throw new MojoExecutionException("You may use an old version of swagger which is not supported by swagger-maven-plugin 3.0+\n" +
                    "swagger-maven-plugin 3.0+ only supports swagger spec 2.0");
        }

        try {
            this.getLog()
                .debug(this.apiSources.toString());

            if (this.enabledObjectMapperFeatures != null) {
                this.configureObjectMapperFeatures(this.enabledObjectMapperFeatures, true);

            }

            if (this.disabledObjectMapperFeatures != null) {
                this.configureObjectMapperFeatures(this.disabledObjectMapperFeatures, false);
            }

            for (final ApiSource apiSource : this.apiSources) {
                this.validateConfiguration(apiSource);
                final AbstractDocumentSource documentSource = apiSource.isSpringmvc()
                        ? new SpringMavenDocumentSource(apiSource, this.getLog(), this.projectEncoding)
                        : new MavenDocumentSource(apiSource, this.getLog(), this.projectEncoding);

                documentSource.loadTypesToSkip();
                documentSource.loadModelModifier();
                documentSource.loadModelConverters();
                documentSource.loadDocuments();

                this.createOutputDirs(apiSource.getOutputPath());

                if (apiSource.getTemplatePath() != null) {
                    documentSource.toDocuments();
                }
                final String swaggerFileName = this.getSwaggerFileName(apiSource.getSwaggerFileName());
                documentSource.toSwaggerDocuments(
                        apiSource.getSwaggerUIDocBasePath() == null
                                ? apiSource.getBasePath()
                                : apiSource.getSwaggerUIDocBasePath(),
                        apiSource.getOutputFormats(), swaggerFileName, this.projectEncoding);

                if (apiSource.isAttachSwaggerArtifact() && apiSource.getSwaggerDirectory() != null && this.project != null) {
                    final String outputFormats = apiSource.getOutputFormats();
                    if (outputFormats != null) {
                        for (final String format : outputFormats.split(",")) {
                            final File swaggerFile = new File(apiSource.getSwaggerDirectory(), swaggerFileName + "." + format.toLowerCase());
                            final String classifier = swaggerFileName.equals("swagger")
                                    ? this.getSwaggerDirectoryName(apiSource.getSwaggerDirectory())
                                    : swaggerFileName;
                            this.projectHelper.attachArtifact(this.project, format.toLowerCase(), classifier, swaggerFile);

                        }
                    }
                }
                final File swaggerFile = new File(apiSource.getSwaggerDirectory(), swaggerFileName + ".json");
                this.notifyCallback(swaggerFile);
            }
        } catch (final GenerateException e) {
            throw new MojoFailureException(e.getMessage(), e);
        } catch (final Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void notifyCallback(final File swaggerFile) {
        if (this.callbacks == null || this.callbacks.isEmpty()) {
            return;
        }
        try {
            final String fileBody = FileUtils.readFileToString(swaggerFile, "UTF-8");
            for (final String callback : this.callbacks) {
                final URL httpUrl = new URL(callback);
                final HttpURLConnection conn = (HttpURLConnection) httpUrl.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("connection", "close");
                conn.setUseCaches(false);//设置不要缓存
                conn.setInstanceFollowRedirects(true);
                conn.setDoOutput(true);
                conn.setDoInput(true);
                conn.setReadTimeout(2000);
                try (
                        OutputStreamWriter out = new OutputStreamWriter(conn.getOutputStream());
                ) {
                    out.write(fileBody);
                    out.flush();
                    out.close();
                    conn.connect();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        while ((reader.readLine()) != null) {
                        }
                    }
                }
                conn.disconnect();
            }
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    private void createOutputDirs(final String outputPath) throws MojoExecutionException {
        if (outputPath != null) {
            final File outputDirectory = new File(outputPath).getParentFile();
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
    private void validateConfiguration(final ApiSource apiSource) throws GenerateException {
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
            final Class<?> tryClass = Class.forName("com.wordnik.swagger.annotations.ApiErrors");
            return true;
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }

    private boolean useSwaggerSpec13() {
        try {
            final Class<?> tryClass = Class.forName("com.wordnik.swagger.model.ApiListing");
            return true;
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }

    private String getSwaggerFileName(final String swaggerFileName) {
        return swaggerFileName == null || "".equals(swaggerFileName.trim()) ? "swagger" : swaggerFileName;
    }

    private String getSwaggerDirectoryName(final String swaggerDirectory) {
        return new File(swaggerDirectory).getName();
    }

    private void configureObjectMapperFeatures(final List<String> features, final boolean enabled) throws Exception {
        for (final String feature : features) {
            final int i = feature.lastIndexOf(".");
            final Class clazz = Class.forName(feature.substring(0, i));
            final Enum e = Enum.valueOf(clazz, feature.substring(i + 1));
            this.getLog()
                .debug("enabling " + e.getDeclaringClass()
                                      .toString() + "." + e.name() + "");
            final Method method = Json.mapper()
                                      .getClass()
                                      .getMethod("configure", e.getClass(), boolean.class);
            method.invoke(Json.mapper(), e, enabled);
        }
    }

}
