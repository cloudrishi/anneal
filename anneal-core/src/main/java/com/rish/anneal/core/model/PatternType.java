package com.rish.anneal.core.model;

public enum PatternType {

    /**
     * Match a specific AST node type — ClassDeclaration, MethodCallExpr, etc.
     * Most precise detection method.
     */
    AST_NODE,

    /**
     * Match an import statement — detects use of a specific package or class.
     * e.g. import sun.misc.Unsafe, import javax.xml.bind.*
     */
    IMPORT,

    /**
     * Match a method call on a specific type.
     * e.g. System.setSecurityManager(), Thread.stop()
     */
    API_CALL,

    /**
     * Match the presence or absence of an annotation.
     * e.g. @Override on finalize(), missing @SuppressWarnings
     */
    ANNOTATION,

    /**
     * Match reflection-based access patterns.
     * e.g. Field.setAccessible(true), getDeclaredFields() on JDK types
     */
    REFLECTION,

    /**
     * Match build configuration — pom.xml, build.gradle properties.
     * e.g. maven.compiler.source < 25, removed JVM flags in surefire config
     */
    BUILD
}
