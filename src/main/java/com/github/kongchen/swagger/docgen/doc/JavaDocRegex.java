package com.github.kongchen.swagger.docgen.doc;

public class JavaDocRegex {

    public final static String PACKAGE = "package [^;]*;";
    public final static String JAVADOC = "\\/\\*\\*((?!\\*\\/).)*\\*\\/";
    public final static String ANNOTATION = "@[a-zA-Z]+(\\([^@)]*\\))?";
    public final static String METHOD = "(public|protected|private|static|\\s) +[\\w\\<\\>\\[\\]]+\\s+(\\w+) *\\([^{]*\\{";
    public final static String FIELD = "(public|protected|private|static|\\s) +[\\w\\<\\>\\[\\]]+\\s+(\\w+) *;";
}
