package com.github.kongchen.swagger.docgen.doc;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
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
public class JavaFile extends VoidVisitorAdapter {

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

    public JavaFile(Path path) throws IOException {
        this.path = path;
        this.packageName = getPackageNameFromFile();
        this.className = path.getFileName()
                             .toString()
                             .replace(".java", "");
        this.visit(StaticJavaParser.parse(path), null);
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

    @Override
    public void visit(MethodDeclaration n, Object arg) {
        String comment = "";
        if (n.getJavadoc().isPresent()){
            comment = n.getJavadoc().get().toText();
            comment = StringUtils.substringBefore(comment, "@");
            comment = comment.replaceAll("\n", "");
        }
        this.methodMap.put(n.getName().asString(), comment);
    }

    @Override
    public void visit(FieldDeclaration n, Object arg) {
        String comment = "";
        if (n.getJavadoc().isPresent()){
            comment = n.getJavadoc().get().toText();
            comment = StringUtils.substringBefore(comment, "@");
            comment = comment.replaceAll("\n", "");
        }
        this.fieldMap.put(n.getVariable(0).getName().asString(), comment);
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


}
