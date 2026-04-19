package com.rish.anneal.api.registry;

import com.rish.anneal.core.model.JavaVersion;
import com.rish.anneal.core.model.MigrationRule;
import com.rish.anneal.core.model.RuleCategory;
import com.rish.anneal.core.rule.ApiRemovalRules;
import com.rish.anneal.core.rule.BuildRules;
import com.rish.anneal.core.rule.ConcurrencyRules;
import com.rish.anneal.core.rule.DeprecationRules;
import com.rish.anneal.core.rule.JpmsRules;
import com.rish.anneal.core.rule.LanguageRules;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Central registry for all migration rules.
 * Rules are injected via CDI — each rule set is a separate injectable bean
 * defined in anneal-core with no framework dependency.
 * The registry aggregates them and provides lookup by id, category, and version boundary.
 */
@ApplicationScoped
public class RuleRegistry {

    private final List<MigrationRule> allRules;
    private final Map<String, MigrationRule> rulesById;

    @Inject
    public RuleRegistry(JpmsRules jpmsRules,
                        ApiRemovalRules apiRemovalRules,
                        DeprecationRules deprecationRules,
                        LanguageRules languageRules,
                        ConcurrencyRules concurrencyRules,
                        BuildRules buildRules) {
        this.allRules = Stream.of(
                        jpmsRules.rules(),
                        apiRemovalRules.rules(),
                        deprecationRules.rules(),
                        languageRules.rules(),
                        concurrencyRules.rules(),
                        buildRules.rules()
                )
                .flatMap(List::stream)
                .toList();

        this.rulesById = allRules.stream()
                .collect(Collectors.toMap(MigrationRule::getRuleId, Function.identity()));
    }

    /**
     * Returns all rules that apply to the given source → target version boundary.
     */
    public List<MigrationRule> rulesFor(JavaVersion source, JavaVersion target) {
        return allRules.stream()
                .filter(rule -> rule.appliesTo(source, target))
                .toList();
    }

    /**
     * Returns all rules in a given category.
     */
    public List<MigrationRule> rulesByCategory(RuleCategory category) {
        return allRules.stream()
                .filter(rule -> rule.getCategory() == category)
                .toList();
    }

    /**
     * Looks up a rule by its unique ruleId.
     */
    public Optional<MigrationRule> findById(String ruleId) {
        return Optional.ofNullable(rulesById.get(ruleId));
    }

    /**
     * Returns all registered rules.
     */
    public List<MigrationRule> allRules() {
        return allRules;
    }

    public int size() {
        return allRules.size();
    }
}
