// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;
import com.microsoft.azure.toolkit.intellij.java.sdk.models.RuleConfig;
import com.microsoft.azure.toolkit.intellij.java.sdk.utils.MavenUtils;
import com.microsoft.azure.toolkit.intellij.java.sdk.utils.RuleConfigLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/**
 * This class is an inspection tool that checks for Kusto queries with time intervals in the query string. This approach
 * makes queries less flexible and harder to troubleshoot. This inspection tool checks for the following anti-patterns:
 * <ul>
 * <li> Queries that use the "ago" function with a time interval.</li>
 * <li> Queries that use the "datetime" function with a specific datetime.</li>
 * <li> Queries that use the "now" function to get the current timestamp.</li>
 * <li> Queries that use the "startofday", "startofmonth", or "startofyear" functions.</li>
 * <li> Queries that use the "between" function with datetime values.</li>
 * </ul>
 * <p>
 * When the anti-patterns are detected as parameters of Azure client method calls, a problem is registered.
 */
public class KustoQueriesWithTimeIntervalInQueryStringCheck extends LocalInspectionTool {

    @NotNull
    @Override
    public JavaElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        // clear the timeIntervalParameters list in every visit
        KustoQueriesVisitor.resetTimeIntervalParameters();
        return new KustoQueriesVisitor(holder);
    }

    /**
     * This class defines the visitor for the inspection tool The visitor checks for Kusto queries with time intervals
     * in the query string and registers a problem if an anti-pattern is detected To check for the anti-patterns, the
     * visitor uses regex patterns to match the query string Processing of polyadic expressions is also done to replace
     * the variables with their values in the query string before checking for the anti-patterns.
     */
    static class KustoQueriesVisitor extends JavaElementVisitor {

        private final ProblemsHolder holder;

        // // Define constants for string literals
        private static final RuleConfig RULE_CONFIG;
        private static final List<Pattern> REGEX_PATTERNS;
        private static final boolean SKIP_WHOLE_RULE;
        private static final List<String> timeIntervalParameters = new ArrayList<>();

        static {
            RuleConfigLoader centralRuleConfigLoader = RuleConfigLoader.getInstance();
            RULE_CONFIG = centralRuleConfigLoader.getRuleConfig("KustoQueriesWithTimeIntervalInQueryStringCheck");
            REGEX_PATTERNS = RULE_CONFIG.getRegexPatternsToCheck().entrySet().stream()
                .filter(entry -> Objects.nonNull(entry.getValue()))
                .map(entry -> Pattern.compile(entry.getValue())) // Compile each pattern into a `Pattern` object
                .toList();

            SKIP_WHOLE_RULE = RULE_CONFIG.skipRuleCheck() || REGEX_PATTERNS.isEmpty();
        }

        /**
         * Constructor for the KustoQueriesVisitor class The constructor initializes the ProblemsHolder and isOnTheFly
         * variables
         *
         * @param holder - ProblemsHolder is used to register problems found in the code
         */
        KustoQueriesVisitor(ProblemsHolder holder) {
            this.holder = holder;
        }

        /** Resets the time interval parameters list. */
        static void resetTimeIntervalParameters() {
            timeIntervalParameters.clear();
        }

        /**
         * This method visits the element and checks for the anti-patterns The method checks if the element is a
         * PsiPolyadicExpression or a PsiLocalVariable If the element is a PsiLocalVariable, the method checks the
         * initializer of the variable If the element is a PsiPolyadicExpression, the method processes the expression to
         * replace the variables with their values The method then checks the expression for the anti-patterns by
         * matching regex patterns with the expression text and registers a problem if an anti-pattern is detected
         *
         * @param element - the element to visit
         */
        @Override
        public void visitElement(@NotNull PsiElement element) {
            super.visitElement(element);

            // Skip the whole rule if the rule configuration is empty
            if (SKIP_WHOLE_RULE) {
                return;
            }

            if (element instanceof PsiMethodCallExpressionImpl) {
                handleMethodCallExpression((PsiMethodCallExpressionImpl) element);
            } else if (element instanceof PsiLocalVariable) {
                handleLocalVariable((PsiLocalVariable) element);
            } else if (element instanceof PsiPolyadicExpression) {
                // if element is a polyadic expression, extract the value and replace the variable with the value
                // PsiPolyadicExpressions are used to represent expressions with multiple operands
                // eg ("datetime" + startDate), where startDate is a variable
                handlePolyadicExpression((PsiPolyadicExpression) element);
            }
        }

        /**
         * This method checks the expression for the anti-patterns by matching regex patterns with the expression text
         * and registers a problem if an anti-pattern is detected
         *
         * @param expression - the expression to check
         * @param element - the element to check
         */
        void checkExpressionForPatterns(PsiExpression expression, PsiElement element) {
            if (expression == null) {
                return;
            }
            String text = expression.getText();

            // Check if the expression text contains any of the regex patterns
            boolean foundAntiPattern = REGEX_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(text).find());


            // If an anti-pattern is detected, register a problem
            if (foundAntiPattern) {
                PsiElement parentElement = element.getParent();

                if (parentElement instanceof PsiLocalVariable) {
                    PsiLocalVariable variable = (PsiLocalVariable) parentElement;
                    String variableName = variable.getName();
                    timeIntervalParameters.add(variableName);
                }
            }
        }

        /**
         * This method handles the local variable by checking the initializer of the variable If the initializer is a
         * PsiLiteralExpression, the method checks the expression for the anti-patterns by matching regex patterns with
         * the expression text
         *
         * @param variable - the local variable to check
         */
        private void handleLocalVariable(PsiLocalVariable variable) {
            PsiExpression initializer = variable.getInitializer();
            if (initializer instanceof PsiLiteralExpression) {
                checkExpressionForPatterns(initializer, variable);
            }
        }

        /**
         * This method handles the polyadic expression by processing the expression to replace the variables with their
         * values The method then checks the expression for the anti-patterns by matching regex patterns with the
         * expression text
         *
         * @param polyadicExpression - the polyadic expression to check
         */
        private void handlePolyadicExpression(PsiPolyadicExpression polyadicExpression) {
            checkExpressionForPatterns(polyadicExpression, polyadicExpression);
        }

        /**
         * This method handles the method call by checking the parameters of the method call If the parameter is a
         * reference to a variable, the method checks the variable name If the variable name is in the list of time
         * interval parameters, the method checks if the method call is an Azure client method call If the method call
         * is an Azure client method call, the method registers a problem
         *
         * @param methodCall - the method call to check
         */
        private void handleMethodCallExpression(PsiMethodCallExpressionImpl methodCall) {
            PsiExpressionList argumentList = methodCall.getArgumentList();
            for (PsiExpression argument : argumentList.getExpressions()) {
                if (argument instanceof PsiReferenceExpression referenceExpression) {
                    PsiElement resolvedElement = referenceExpression.resolve();
                    // check if the variable name is in the list of time interval parameters
                    // if the variable name is in the list, check if the method call is an Azure client method call
                    if (resolvedElement instanceof PsiVariable variable && timeIntervalParameters.contains(variable.getName())) {
                        if (MavenUtils.isAzureClientMethodCall(methodCall)) {
                            holder.registerProblem(methodCall, RULE_CONFIG.getAntiPatternMessage());
                        }
                    }
                }
            }
        }
    }
}
