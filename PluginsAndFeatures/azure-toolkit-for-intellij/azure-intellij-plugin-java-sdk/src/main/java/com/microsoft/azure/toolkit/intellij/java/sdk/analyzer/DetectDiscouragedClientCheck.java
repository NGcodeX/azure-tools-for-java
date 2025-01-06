// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.microsoft.azure.toolkit.intellij.java.sdk.models.RuleConfig;
import com.microsoft.azure.toolkit.intellij.java.sdk.utils.RuleConfigLoader;
import org.jetbrains.annotations.NotNull;

/**
 * This class extends the LocalInspectionTool to check for the use of discouraged clients in the code and suggests using
 * other clients instead. The client data is loaded from the configuration file and the client name is checked against
 * the discouraged client name. If the client name matches, a problem is registered with the suggestion message.
 */
public abstract class DetectDiscouragedClientCheck extends LocalInspectionTool {
    /**
     * Provides the specific RuleConfig for the subclass context.
     *
     * @return RuleConfig for the subclass
     */
    protected abstract RuleConfig getRuleConfig();

    /**
     * This method builds a visitor to check for the discouraged client name in the code. If the client name matches the
     * discouraged client, a problem is registered with the suggestion message.
     *
     * @param holder - the ProblemsHolder object to register the problem
     * @param isOnTheFly - whether the inspection is on the fly - not used in this implementation but required by the
     * parent class
     */
    @NotNull
    @Override
    public JavaElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        RuleConfig ruleConfig = getRuleConfig();
        return new DetectDiscouragedClientVisitor(holder, ruleConfig);
    }

    /**
     * This class is a visitor that checks for the use of discouraged clients in the code. If the client name matches
     * the discouraged client, a problem is registered with the suggestion message.
     */
    static class DetectDiscouragedClientVisitor extends JavaElementVisitor {

        private final ProblemsHolder holder;
        private final RuleConfig ruleConfig;

        public DetectDiscouragedClientVisitor(ProblemsHolder holder, RuleConfig ruleConfig) {
            this.holder = holder;
            this.ruleConfig = ruleConfig;
        }

        /**
         * This method builds a visitor to check for the discouraged client name in the code. If the client name matches
         * the discouraged client, a problem is registered with the suggestion message.
         */
        @Override
        public void visitTypeElement(PsiTypeElement element) {
            super.visitTypeElement(element);

            // Skip the rule if the configuration is empty or the rule is disabled
            if (ruleConfig.skipRuleCheck()) {
                return;
            }

            // Ensure the element's type is not null
            PsiType psiType = element.getType();
            if (psiType == null) {
                return;
            }

            // Get the presentable text for the type
            String elementType = psiType.getPresentableText();

            // Check if the type matches any discouraged usage
            if (!ruleConfig.getUsagesToCheck().contains(elementType)) {
                return;
            }

            // Register a problem with the corresponding anti-pattern message
            holder.registerProblem(element, ruleConfig.getAntiPatternMessage());
        }
    }
}
