package com.github.kongchen.swagger.docgen.doc;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * java原文件路径
 */
public class JavaFile {

    private Path path;

    private String packageName;

    private String className;

    private Map<String, String> methodMap = new HashMap<>();

    private Map<String, String> fieldMap = new HashMap<>();

    public String getPackageName() {
        return packageName;
    }

    public String getClassName() {
        return className;
    }

    public JavaFile(Path path) {
        this.path = path;
        this.packageName = getPackageNameFromFile();
        this.className = path.getFileName()
                             .toString()
                             .replace(".java", "");
        this.getMethodsFromJavaFile();
        this.getFieldsFromJavaField();
    }

    public String getMethod(Method method) {
        String result = methodMap.get(method.getName());
        return StringUtils.isBlank(result) ? "" : result;
    }

    public String getField(Field field) {
        String result = fieldMap.get(field.getName());
        return StringUtils.isBlank(result) ? "" : result;
    }


    private String getFileString() {
        try {
            return FileUtils.readFileToString(path.toFile(), "utf-8");
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * @return
     */
    private String getPackageNameFromFile() {
        String fileString = getFileString();
        Pattern pattern = Pattern.compile(JavaDocRegex.PACKAGE);
        Matcher matcher = pattern.matcher(fileString);
        if (matcher.find()) {
            return fileString.substring(matcher.start() + 8, matcher.end() - 1);
        }
        return null;
    }

    /**
     *
     */
    public void getMethodsFromJavaFile() {
        String fileString = getFileString();
        String sectionRegex = JavaDocRegex.JAVADOC + "([\\s]*" + JavaDocRegex.ANNOTATION + ")*[\\s]*" + JavaDocRegex.METHOD;
        Pattern pattern = Pattern.compile(sectionRegex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(fileString);
        while (matcher.find()) {
            String section = fileString.substring(matcher.start(), matcher.end());
            String javadocSection = findJavadocSectionByRegexInSection(section, JavaDocRegex.JAVADOC);
            methodMap.put(removeJavadocCharactersFromString(section), javadocSection);
        }
    }

    /**
     *
     */
    public void getFieldsFromJavaField() {
        String fileString = getFileString();
        String sectionRegex = JavaDocRegex.JAVADOC + JavaDocRegex.FIELD;
        Pattern pattern = Pattern.compile(sectionRegex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(fileString);
        while (matcher.find()) {
            String section = fileString.substring(matcher.start(), matcher.end());
            String javadocSection = findJavadocSectionByRegexInSection(section, JavaDocRegex.JAVADOC);
            fieldMap.put(removeJavadocCharactersFromString(section), javadocSection);
        }
    }

    private String findJavadocSectionByRegexInSection(String section, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(section);
        if (matcher.find()) {
            int javadocEnd = matcher.end();
            return section.substring(matcher.start(), matcher.end());
        }
        return "";
    }

    private String removeJavadocCharactersFromString(String str) {
        return str.replace("/**", "")
                  .replace("*/", "")
                  .replace("*", "")
                  .trim()
                  .replaceAll("[\\s]+", " ");
    }

    public static void main(String[] args) {
        JavaFile javaFile = new JavaFile(Paths.get("/Users/Mac/Desktop/git-work/card-bff/card-bff-app/src/main/java/com/qianmi/card/controller/ArchiveController.java"));
        System.out.println(javaFile);
    }
}
