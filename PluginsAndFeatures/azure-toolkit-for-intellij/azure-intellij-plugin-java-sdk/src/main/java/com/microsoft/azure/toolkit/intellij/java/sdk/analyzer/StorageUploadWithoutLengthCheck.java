// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiType;
import com.microsoft.azure.toolkit.intellij.java.sdk.models.RuleConfig;
import com.microsoft.azure.toolkit.intellij.java.sdk.utils.RuleConfigLoader;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Inspection tool to enforce usage of Azure Storage upload APIs with a 'length' parameter.
 */
public class StorageUploadWithoutLengthCheck extends LocalInspectionTool {
    private static final RuleConfig RULE_CONFIG;
    private static final String LENGTH_TYPE = "long";
    private static final boolean SKIP_WHOLE_RULE;

    static {
        String ruleName = "StorageUploadWithoutLengthCheck";
        RuleConfigLoader configLoader = RuleConfigLoader.getInstance();
        RULE_CONFIG = configLoader.getRuleConfig(ruleName);
        SKIP_WHOLE_RULE = RULE_CONFIG.skipRuleCheck();
    }

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new StorageUploadVisitor(holder);
    }

    /**
     * Visitor class to check for Azure Storage upload APIs without a 'length' parameter.
     */
    static class StorageUploadVisitor extends JavaRecursiveElementWalkingVisitor {
        private final ProblemsHolder holder;
        private static final RuleConfig RULE_CONFIG;
        private static final boolean SKIP_WHOLE_RULE;
        private static final List<String> SCOPE_TO_CHECK;

        static {
            RuleConfigLoader configLoader = RuleConfigLoader.getInstance();
            RULE_CONFIG = configLoader.getRuleConfig("StorageUploadWithoutLengthCheck");
            SKIP_WHOLE_RULE = RULE_CONFIG.skipRuleCheck();
            SCOPE_TO_CHECK = RULE_CONFIG.getScopeToCheck();
        }

        StorageUploadVisitor(ProblemsHolder holder) {
            this.holder = holder;
        }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);

            if (SKIP_WHOLE_RULE) {
                return;
            }

            String methodName = expression.getMethodExpression().getReferenceName();
            if (!RULE_CONFIG.getUsagesToCheck().contains(methodName)) {
                return;
            }

            if (!isInScope(expression)) {
                return;
            }

            if (!hasLengthArgument(expression)) {
                holder.registerProblem(expression, RULE_CONFIG.getAntiPatternMessage());
            }
        }

        /**
         * Checks if the method call expression belongs to a class within the specified package scope.
         *
         * @param expression The method call expression to check.
         *
         * @return true if the method call is within the specified package scope, false otherwise.
         */
        private boolean isInScope(PsiMethodCallExpression expression) {
            // Get the qualifier expression and resolve its type
            PsiExpression qualifierExpression = expression.getMethodExpression().getQualifierExpression();
            if (qualifierExpression == null) {
                return false;
            }

            PsiType qualifierType = qualifierExpression.getType();
            if (!(qualifierType instanceof PsiClassType)) {
                return false;
            }

            PsiClass containingClass = ((PsiClassType) qualifierType).resolve();
            if (containingClass == null) {
                return false;
            }

            // Check the class name against the specified scopes
            String qualifiedName = containingClass.getQualifiedName();
            if (qualifiedName == null) {
                return false;
            }

            for (String scope : SCOPE_TO_CHECK) {
                if (qualifiedName.startsWith(scope)) {
                    return true;
                }
            }

            return false;
        }

        /**
         * Checks if a method call expression includes a 'long' type argument directly or in its call chain.
         *
         * @param expression The method call expression to analyze.
         *
         * @return true if a 'long' type argument is found, false otherwise.
         */
        private boolean hasLengthArgument(PsiMethodCallExpression expression) {
            PsiExpression[] arguments = expression.getArgumentList().getExpressions();
            for (PsiExpression arg : arguments) {
                if (isLongType(arg) || isLongTypeInCallChain(arg)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Checks if an expression is of type 'long'.
         *
         * @param expression The expression to check.
         *
         * @return true if the expression is of type 'long', false otherwise.
         */
        private boolean isLongType(PsiExpression expression) {
            return expression.getType() != null && LENGTH_TYPE.equals(expression.getType().getCanonicalText());
        }

        /**
         * Analyzes the method call chain to determine if any argument in the chain is of type 'long'.
         *
         * @param expression The starting expression in the call chain.
         *
         * @return true if a 'long' type argument is found, false otherwise.
         */
        private boolean isLongTypeInCallChain(PsiExpression expression) {
            while (expression instanceof PsiMethodCallExpression) {
                PsiMethodCallExpression methodCall = (PsiMethodCallExpression) expression;
                expression = methodCall.getMethodExpression().getQualifierExpression();

                if (expression instanceof PsiNewExpression &&
                    hasLongConstructorArgument((PsiNewExpression) expression)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Checks if a constructor has an argument of type 'long'.
         *
         * @param newExpression The new expression representing the constructor call.
         *
         * @return true if a 'long' type argument is found, false otherwise.
         */
        private boolean hasLongConstructorArgument(PsiNewExpression newExpression) {
            PsiExpressionList argumentList = newExpression.getArgumentList();
            if (argumentList == null) {
                return false;
            }

            for (PsiExpression arg : argumentList.getExpressions()) {
                if (isLongType(arg)) {
                    return true;
                }
            }
            return false;
        }
    }
}


