// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiMethodCallExpression;
import com.microsoft.azure.toolkit.intellij.java.sdk.models.RuleConfig;
import com.microsoft.azure.toolkit.intellij.java.sdk.utils.RuleConfigLoader;
import org.jetbrains.annotations.NotNull;

/**
 * This class extends the AbstractUpdateCheckpointChecker class to check for the usage of the updateCheckpointAsync()
 * method call in the code. The visitor inspects the method call expressions and checks if the method call is
 * updateCheckpointAsync(). If the method call is updateCheckpointAsync() and the following method is subscribe, a
 * problem is registered.
 */
public class UpdateCheckpointAsyncSubscribeChecker extends AsyncSubscribeChecker {

    /**
     * This class extends the JavaElementVisitor to visit the elements in the code. It checks if the method call is
     * updateCheckpointAsync() and if the following method is `subscribe`. If both conditions are met, a problem is
     * registered with the suggestion message.
     */
    static class UpdateCheckpointAsyncVisitor extends JavaElementVisitor {

        // Define the holder to register problems
        private final ProblemsHolder holder;

        private static final RuleConfig RULE_CONFIG;
        private static final boolean SKIP_WHOLE_RULE;

        /**
         * Constructor to initialize the visitor with the holder and isOnTheFly flag.
         *
         * @param holder ProblemsHolder to register problems
         * @param isOnTheFly boolean to check if the inspection is on the fly. If true, the inspection is performed as
         * you type.
         */
        UpdateCheckpointAsyncVisitor(ProblemsHolder holder, boolean isOnTheFly) {
            this.holder = holder;
        }

        static {
            final String ruleName = "UpdateCheckpointAsyncSubscribeChecker";
            RuleConfigLoader centralRuleConfigLoader = RuleConfigLoader.getInstance();

            // Get the RuleConfig object for the rule
            RULE_CONFIG = centralRuleConfigLoader.getRuleConfig(ruleName);
            SKIP_WHOLE_RULE = RULE_CONFIG.skipRuleCheck();
        }

        /**
         * This method visits the method call expressions in the code. It checks if the method call is
         * updateCheckpointAsync() and if the following method is `subscribe`. If both conditions are met, a problem is
         * registered with the suggestion message.
         *
         * @param expression The method call expression to inspect
         */
        @Override
        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);

            // Check if the rule should be skipped
            if (SKIP_WHOLE_RULE) {
                return;
            }

            expression.getMethodExpression();
            if (expression.getMethodExpression().getReferenceName() == null) {
                return;
            }

            // Check if the method call is updateCheckpointAsync()
            if (RULE_CONFIG.getUsagesToCheck().stream()
                .anyMatch(usage -> usage.equals(expression.getMethodExpression().getReferenceName()))) {

                // Check if the following method is `subscribe` and
                // Check if the updateCheckpointAsync() method call is called on an provided context
                // (EventBatchContext) object
                if (isFollowingMethodSubscribe(expression) && isCalledInProvidedContext(expression, RULE_CONFIG.getScopeToCheck())) {
                    holder.registerProblem(expression, RULE_CONFIG.getAntiPatternMessage());
                }
            }
        }
    }
}