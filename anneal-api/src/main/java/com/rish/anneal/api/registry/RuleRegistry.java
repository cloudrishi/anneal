package com.rish.anneal.api.registry;

import com.rish.anneal.core.model.JavaVersion;
import com.rish.anneal.core.model.MigrationRule;
import com.rish.anneal.core.model.RuleCategory;
import com.rish.anneal.core.rule.*;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Central registry for all migration rules.
 * Rule sets are plain Java — instantiated directly, no CDI injection needed.
 * anneal-core stays zero-dependency pure Java.
 */
@ApplicationScoped
public class RuleRegistry {

    private final List<MigrationRule> allRules;
    private final Map<String, MigrationRule> rulesById;

    public RuleRegistry() {
        this.allRules = Stream.of(
                        new JpmsRules().rules(),
                        new ApiRemovalRules().rules(),
                        new DeprecationRules().rules(),
                        new LanguageRules().rules(),
                        new ConcurrencyRules().rules(),
                        new BuildRules().rules()
                )
                .flatMap(List::stream)
                .toList();

        this.rulesById = allRules.stream()
                .collect(Collectors.toMap(MigrationRule::getRuleId, Function.identity()));
    }

    public List<MigrationRule> rulesFor(JavaVersion source, JavaVersion target) {
        return allRules.stream()
                .filter(rule -> rule.appliesTo(source, target))
                .toList();
    }

    public List<MigrationRule> rulesByCategory(RuleCategory category) {
        return allRules.stream()
                .filter(rule -> rule.getCategory() == category)
                .toList();
    }

    public Optional<MigrationRule> findById(String ruleId) {
        return Optional.ofNullable(rulesById.get(ruleId));
    }

    public List<MigrationRule> allRules() {
        return allRules;
    }

    public int size() {
        return allRules.size();
    }
}