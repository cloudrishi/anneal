package com.rish.anneal.core.rule;

import com.rish.anneal.core.model.DetectionPattern;
import com.rish.anneal.core.model.Effort;
import com.rish.anneal.core.model.FixSuggestion;
import com.rish.anneal.core.model.FixType;
import com.rish.anneal.core.model.JavaVersion;
import com.rish.anneal.core.model.MigrationRule;
import com.rish.anneal.core.model.PatternType;
import com.rish.anneal.core.model.RuleCategory;
import com.rish.anneal.core.model.Severity;

import java.util.List;

/**
 * Concurrency modernization rules.
 * Java 25 ships structured concurrency and scoped values as stable features.
 * Virtual threads (Project Loom) are stable since Java 21.
 * These rules surface opportunities to adopt modern concurrency primitives.
 */
public class ConcurrencyRules {

    public List<MigrationRule> rules() {
        return List.of(
                threadToVirtualThread(),
                threadLocalToScopedValue(),
                synchronizedBlock()
        );
    }

    private MigrationRule threadToVirtualThread() {
        return MigrationRule.builder()
                .ruleId("CONCURRENCY_THREAD_VIRTUAL")
                .name("Platform thread — consider virtual thread")
                .category(RuleCategory.CONCURRENCY)
                .severity(Severity.MODERNIZATION)
                .effort(Effort.MEDIUM)
                .introducedIn(JavaVersion.V21)
                .patterns(List.of(
                        DetectionPattern.builder()
                                .type(PatternType.AST_NODE)
                                .matcher("new Thread")
                                .nodeType("ObjectCreationExpr")
                                .confidence(0.6f)
                                .build(),
                        DetectionPattern.builder()
                                .type(PatternType.API_CALL)
                                .matcher("java.util.concurrent.Executors#newFixedThreadPool()")
                                .confidence(0.7f)
                                .build(),
                        DetectionPattern.builder()
                                .type(PatternType.API_CALL)
                                .matcher("java.util.concurrent.Executors#newCachedThreadPool()")
                                .confidence(0.7f)
                                .build()
                ))
                .fixTemplate(FixSuggestion.builder()
                        .fixType(FixType.REFACTOR)
                        .originalCode("""
                                Thread t = new Thread(() -> doWork());
                                t.start();
                                // or
                                ExecutorService pool = Executors.newFixedThreadPool(10);
                                """)
                        .suggestedCode("""
                                // Virtual thread — lightweight, no pooling needed:
                                Thread t = Thread.ofVirtual().start(() -> doWork());
                                // or
                                ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
                                // or with structured concurrency (Java 25):
                                try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                                    scope.fork(() -> doWork());
                                    scope.join().throwIfFailed();
                                }
                                """)
                        .autoApplicable(false)
                        .build())
                .referenceUrl("https://openjdk.org/jeps/444")
                .build();
    }

    private MigrationRule threadLocalToScopedValue() {
        return MigrationRule.builder()
                .ruleId("CONCURRENCY_THREADLOCAL_SCOPED_VALUE")
                .name("ThreadLocal — consider ScopedValue")
                .category(RuleCategory.CONCURRENCY)
                .severity(Severity.MODERNIZATION)
                .effort(Effort.MEDIUM)
                .introducedIn(JavaVersion.V25)
                .patterns(List.of(
                        DetectionPattern.builder()
                                .type(PatternType.IMPORT)
                                .matcher("java.lang.ThreadLocal")
                                .confidence(0.8f)
                                .build(),
                        DetectionPattern.builder()
                                .type(PatternType.AST_NODE)
                                .matcher("ThreadLocal")
                                .nodeType("ClassOrInterfaceType")
                                .confidence(0.8f)
                                .build()
                ))
                .fixTemplate(FixSuggestion.builder()
                        .fixType(FixType.REFACTOR)
                        .originalCode("""
                                private static final ThreadLocal<User> CURRENT_USER =
                                    ThreadLocal.withInitial(() -> null);
                                CURRENT_USER.set(user);
                                User u = CURRENT_USER.get();
                                """)
                        .suggestedCode("""
                                // ScopedValue — immutable, inheritable, safer with virtual threads:
                                private static final ScopedValue<User> CURRENT_USER = ScopedValue.newInstance();
                                ScopedValue.where(CURRENT_USER, user).run(() -> {
                                    User u = CURRENT_USER.get();
                                });
                                """)
                        .autoApplicable(false)
                        .build())
                .referenceUrl("https://openjdk.org/jeps/487")
                .build();
    }

    private MigrationRule synchronizedBlock() {
        return MigrationRule.builder()
                .ruleId("CONCURRENCY_SYNCHRONIZED_STRUCTURED")
                .name("synchronized block — consider structured concurrency")
                .category(RuleCategory.CONCURRENCY)
                .severity(Severity.MODERNIZATION)
                .effort(Effort.HIGH)
                .introducedIn(JavaVersion.V25)
                .patterns(List.of(
                        DetectionPattern.builder()
                                .type(PatternType.AST_NODE)
                                .matcher("SynchronizedStmt")
                                .nodeType("SynchronizedStmt")
                                .confidence(0.5f)
                                .build()
                ))
                .fixTemplate(FixSuggestion.builder()
                        .fixType(FixType.MANUAL)
                        .originalCode("""
                                synchronized (lock) {
                                    result1 = fetchA();
                                    result2 = fetchB();
                                }
                                """)
                        .suggestedCode("""
                                // Structured concurrency (Java 25) for concurrent subtasks:
                                try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                                    var f1 = scope.fork(() -> fetchA());
                                    var f2 = scope.fork(() -> fetchB());
                                    scope.join().throwIfFailed();
                                    result1 = f1.get();
                                    result2 = f2.get();
                                }
                                """)
                        .autoApplicable(false)
                        .build())
                .referenceUrl("https://openjdk.org/jeps/505")
                .build();
    }
}
