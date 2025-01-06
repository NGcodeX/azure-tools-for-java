// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.microsoft.azure.toolkit.intellij.java.sdk.models.RuleConfig;
import com.microsoft.azure.toolkit.intellij.java.sdk.utils.MavenUtils;
import com.microsoft.azure.toolkit.intellij.java.sdk.utils.RuleConfigLoader;
import org.jetbrains.annotations.NotNull;

/**
 * Inspection tool to detect the use of getSyncPoller() on a PollerFlux. The inspection will check if the method call is
 * on a PollerFlux and if the method call is on an Azure SDK client. If both conditions are met, the inspection will
 * register a problem with the suggestion to use SyncPoller instead.
 *
 * This is an example of an anti-pattern that would be detected by the inspection tool.
 * public void exampleUsage() {
 *     PollerFlux<String> pollerFlux = createPollerFlux();
 *
 *     // Anti-pattern: Using getSyncPoller() on PollerFlux
 *     SyncPoller<String, Void> syncPoller = pollerFlux.getSyncPoller();
 * }
 */
public class GetSyncPollerOnPollerFluxCheck extends LocalInspectionTool {

    /**
     * Method to build the visitor for the inspection tool.
     *
     * @param holder Holder for the problems found by the inspection
     *
     * @return JavaElementVisitor a visitor to visit the method call expressions
     */
    @NotNull
    @Override
    public JavaElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new GetSyncPollerOnPollerFluxVisitor(holder);
    }

    /**
     * Visitor class to visit the method call expressions and check for the use of getSyncPoller() on a PollerFlux. The
     * visitor will check if the method call is on a PollerFlux and if the method call is on an Azure SDK client.
     */
    class GetSyncPollerOnPollerFluxVisitor extends JavaElementVisitor {

        private final ProblemsHolder holder;

        private static final RuleConfig RULE_CONFIG;
        private static final boolean SKIP_WHOLE_RULE;

        static {
            final String ruleName = "GetSyncPollerOnPollerFluxCheck";
            RuleConfigLoader centralRuleConfigLoader = RuleConfigLoader.getInstance();

            // Get the RuleConfig object for the rule
            RULE_CONFIG = centralRuleConfigLoader.getRuleConfig(ruleName);
            SKIP_WHOLE_RULE = RULE_CONFIG.skipRuleCheck();
        }

        /**
         * Constructor to initialize the visitor with the holder and isOnTheFly flag.
         *
         * @param holder Holder for the problems found by the inspection
         */
        public GetSyncPollerOnPollerFluxVisitor(ProblemsHolder holder) {
            this.holder = holder;
        }

        /**
         * Method to visit the method call expressions and check for the use of getSyncPoller() on a PollerFlux.
         *
         * @param expression Method call expression to visit
         */
        @Override
        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);

            // Check if the whole rule should be skipped
            if (SKIP_WHOLE_RULE) {
                return;
            }

            if (isGetSyncPollerCall(expression) && isAsyncContext(expression) && MavenUtils.isAzureClientMethodCall(expression)) {
                holder.registerProblem(expression, RULE_CONFIG.getAntiPatternMessage());
            }
        }

        /**
         * Helper method to check if the method call is on a PollerFlux type.
         *
         * @param expression Method call expression to check
         *
         * @return true if the method call is a getSyncPoller() call, false otherwise
         */
        private boolean isGetSyncPollerCall(@NotNull PsiMethodCallExpression expression) {
            for (String usage : RULE_CONFIG.getUsagesToCheck()) {
                if (expression.getMethodExpression().getReferenceName().startsWith(usage)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Helper method to check if the method call is on a PollerFlux type.
         *
         * @param methodCall Method call expression to check
         *
         * @return true if the method call is on a reactive type, false otherwise
         */
        private boolean isAsyncContext(@NotNull PsiMethodCallExpression methodCall) {
            PsiExpression qualifier = methodCall.getMethodExpression().getQualifierExpression();
            if (qualifier == null) {
                return false;
            }

            PsiType type = qualifier.getType();
            String typeName = type.getCanonicalText();
            if (typeName != null && typeName.contains("PollerFlux")) {
                return true;
            }
            return false;
        }
    }
}
