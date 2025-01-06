// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiNewExpression;
import com.microsoft.azure.toolkit.intellij.java.sdk.models.RuleConfig;
import com.microsoft.azure.toolkit.intellij.java.sdk.utils.RuleConfigLoader;
import org.jetbrains.annotations.NotNull;

/**
 * Custom inspection tool to check for hardcoded API keys and tokens in the code.
 * Flags instances such as:
 * 1. TextAnalyticsClient client = new TextAnalyticsClientBuilder()
 *    .endpoint(endpoint)
 *    .credential(new AzureKeyCredential(apiKey))
 *    .buildClient();
 * 2. TokenCredential credential = request -> {
 *    AccessToken token = new AccessToken("<your-hardcoded-token>", OffsetDateTime.now().plusHours(1));
 * }
 */
public class HardcodedAPIKeysAndTokensCheck extends LocalInspectionTool {

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new APIKeysAndTokensVisitor(holder);
    }

    /**
     * Visitor to inspect Java elements for hardcoded API keys and tokens.
     */
    static class APIKeysAndTokensVisitor extends JavaElementVisitor {

        private final ProblemsHolder holder;
        private static final RuleConfig RULE_CONFIG;
        private static final boolean SKIP_WHOLE_RULE;

        static {
            RuleConfigLoader ruleConfigLoader = RuleConfigLoader.getInstance();
            RULE_CONFIG = ruleConfigLoader.getRuleConfig("HardcodedAPIKeysAndTokensCheck");
            SKIP_WHOLE_RULE = RULE_CONFIG.skipRuleCheck();
        }

        APIKeysAndTokensVisitor(ProblemsHolder holder) {
            this.holder = holder;
        }

        @Override
        public void visitElement(@NotNull PsiElement element) {
            if (element instanceof PsiNewExpression newExpression) {
                checkNewExpression(newExpression);
            }
        }

        private void checkNewExpression(@NotNull PsiNewExpression newExpression) {
            var classReference = newExpression.getClassReference();
            if (classReference == null) {
                return;
            }

            String qualifiedName = classReference.getQualifiedName();
            String referenceName = classReference.getReferenceName();

            if (qualifiedName != null && qualifiedName.startsWith(RuleConfig.AZURE_PACKAGE_NAME)
                && RULE_CONFIG.getUsagesToCheck().contains(referenceName)) {
                checkForHardcodedStrings(newExpression);
            }
        }

        private void checkForHardcodedStrings(@NotNull PsiNewExpression newExpression) {
            for (PsiElement child : newExpression.getChildren()) {
                if (child instanceof PsiLiteralExpression literal && literal.getValue() instanceof String) {
                    holder.registerProblem(newExpression, RULE_CONFIG.getAntiPatternMessage());
                }
            }
        }
    }
}

