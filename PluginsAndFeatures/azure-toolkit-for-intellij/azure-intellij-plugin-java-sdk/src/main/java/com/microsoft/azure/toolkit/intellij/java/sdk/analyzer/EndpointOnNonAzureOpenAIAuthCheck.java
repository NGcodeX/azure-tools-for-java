// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.microsoft.azure.toolkit.intellij.java.sdk.models.RuleConfig;
import com.microsoft.azure.toolkit.intellij.java.sdk.utils.RuleConfigLoader;
import org.jetbrains.annotations.NotNull;

/**
 * This class is a LocalInspectionTool that checks if the endpoint method is used with KeyCredential for non-Azure
 * OpenAI clients. If the endpoint method is used with KeyCredential for non-Azure OpenAI clients, a warning is
 * registered.
 */
public class EndpointOnNonAzureOpenAIAuthCheck extends LocalInspectionTool {

    /**
     * This method builds the visitor for the inspection tool.
     *
     * @param holder The ProblemsHolder object that holds the problems found by the inspection tool.
     * @param isOnTheFly A boolean that indicates if the inspection tool is running on the fly. - this is not used in
     * this implementation but is required by the method signature.
     *
     * @return The JavaElementVisitor object that will be used to visit the elements in the code.
     */
    @NotNull
    @Override
    public JavaElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new EndpointOnNonAzureOpenAIAuthVisitor(holder);
    }

    /**
     * This class is a JavaElementVisitor that visits the elements in the code and checks if the endpoint method is used
     * with KeyCredential for non-Azure OpenAI clients.
     */
    static class EndpointOnNonAzureOpenAIAuthVisitor extends JavaElementVisitor {

        private final ProblemsHolder holder;
        private static final RuleConfig RULE_CONFIG;

        // The boolean that indicates if the rule should be skipped
        private static final boolean SKIP_WHOLE_RULE;

        /**
         * Constructor for the visitor.
         *
         * @param holder The ProblemsHolder object that holds the problems found by the inspection tool.
         */
        EndpointOnNonAzureOpenAIAuthVisitor(ProblemsHolder holder) {
            this.holder = holder;
        }

        // Static initializer block to load the configurations once
        static {
            RuleConfigLoader centralRuleConfigLoader = RuleConfigLoader.getInstance();
            RULE_CONFIG = centralRuleConfigLoader.getRuleConfig("EndpointOnNonAzureOpenAIAuthCheck");
            SKIP_WHOLE_RULE = RULE_CONFIG.skipRuleCheck();
        }

        /**
         * This method visits the method call expressions in the code. If the method call expression is the endpoint
         * method, the method call chain is checked to see if it is a non-Azure OpenAI client. If the method call chain
         * uses the credential method with KeyCredential for non-Azure OpenAI clients, a warning is registered.
         *
         * @param expression The method call expression to visit.
         */
        @Override
        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);

            if (SKIP_WHOLE_RULE) {
                return;
            }

            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            String methodName = methodExpression.getReferenceName();

            // Skip if method is not the endpoint method or isn't in the list of check items
            if (!"endpoint".equals(methodName) || !RULE_CONFIG.getUsagesToCheck().contains(methodName)) {
                return;
            }

            // Using KeyCredential indicates authentication of a non-Azure OpenAI client
            // If the endpoint method is used with KeyCredential for non-Azure OpenAI clients, a warning is registered
            if (isUsingKeyCredential(expression)) {
                holder.registerProblem(expression, RULE_CONFIG.getAntiPatternMessage());
            }
        }

        /**
         * This method checks if the method call chain uses the credential method with KeyCredential. If this is the
         * case, the method call chain is checked to see if it is a non-Azure OpenAI client.
         *
         * @param expression The method call expression to check.
         *
         * @return True if the method call chain uses the credential method with KeyCredential, false otherwise.
         */
        private static boolean isUsingKeyCredential(PsiMethodCallExpression expression) {

            PsiExpression qualifier = expression.getMethodExpression().getQualifierExpression();

            while (qualifier instanceof PsiMethodCallExpression) {
                PsiMethodCallExpression methodCall = (PsiMethodCallExpression) qualifier;
                String methodName = methodCall.getMethodExpression().getReferenceName();

                // Short-circuit if method is not relevant
                if (!RULE_CONFIG.getUsagesToCheck().contains(methodName)) {
                    return false;
                }

                if ("credential".equals(methodName)) {
                    PsiExpression[] args = methodCall.getArgumentList().getExpressions();
                    // Return early if arguments are incorrect
                    if (args.length != 1 || !isKeyCredential(args[0])) {
                        return false;
                    }

                    return isNonAzureOpenAIClient(methodCall);
                }
                qualifier = methodCall.getMethodExpression().getQualifierExpression();
            }
            return false;
        }

        /**
         * This method checks if the expression is a KeyCredential.
         *
         * @param expression The expression to check.
         *
         * @return True if the expression is a KeyCredential, false otherwise.
         */
        private static boolean isKeyCredential(PsiExpression expression) {
            if (expression instanceof PsiNewExpression) {
                String className = ((PsiNewExpression) expression).getClassReference().getReferenceName();
                return RULE_CONFIG.getScopeToCheck().contains(className);
            }
            return false;
        }

        /**
         * This method checks if the method call chain is on a non-Azure OpenAI client.
         *
         * @param expression The method call expression to check.
         *
         * @return True if the client is a non-Azure OpenAI client, false otherwise.
         */
        private static boolean isNonAzureOpenAIClient(PsiMethodCallExpression expression) {
            PsiElement parent = expression.getParent();
            while (parent instanceof PsiVariable) {
                PsiType type = ((PsiVariable) parent).getType();
                if (type.getCanonicalText().startsWith("com.azure.ai.openai")) {
                    return true;
                }
                parent = parent.getParent();
            }
            return false;
        }
    }
}
