package com.github.kongchen.swagger.docgen.doc;


import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JavaDoc {
    private static final JavaDoc instance = new JavaDoc();

    public static JavaDoc getInstance() {
        return instance;
    }

    private Map<String, JavaFile> javaFileHashMap = new HashMap<>();

    public String getMethodName(Method method) {
        JavaFile javaFile = getJavaFile(method.getDeclaringClass()
                                              .getName());
        if (javaFile != null) {
            return javaFile.getMethod(method);
        }
        return "";
    }

    public String getFieldName(Field field) {
        JavaFile javaFile = getJavaFile(field.getDeclaringClass()
                                             .getName());
        if (javaFile != null) {
            return javaFile.getField(field);
        }
        return "";
    }

    private JavaFile getJavaFile(String name) {
        return javaFileHashMap.get(name);
    }

    public void init(MavenProject project) {
        List<?> rootDirectories = project.getCompileSourceRoots();
        for (Object raw : rootDirectories) {
            String rootDirectory = String.class.cast(raw);
            try {
                loadJavaFile(Paths.get(rootDirectory));
            } catch (IOException e) {
            }
        }
    }


    /**
     * 读取java源文件
     *
     * @param dir
     * @throws IOException
     */
    private void loadJavaFile(Path dir) throws IOException {
        DirectoryStream<Path> stream = null;
        try {
            stream = Files.newDirectoryStream(dir);
            for (Path path : stream) {
                if (path.toFile()
                        .isDirectory()) {
                    loadJavaFile(path);
                } else if (path.getFileName()
                               .toString()
                               .endsWith(".java")) {

                    JavaFile javaFile = new JavaFile(path);

                    //记录
                    javaFileHashMap.put(
                            javaFile.getPackageName() + "." + javaFile.getClassName(),
                            new JavaFile(path)
                    );
                }
            }
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
    }
}
