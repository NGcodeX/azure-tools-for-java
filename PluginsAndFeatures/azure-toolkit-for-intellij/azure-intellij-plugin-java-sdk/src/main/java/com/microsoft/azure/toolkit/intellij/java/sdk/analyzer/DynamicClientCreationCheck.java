// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.microsoft.azure.toolkit.intellij.java.sdk.models.RuleConfig;
import com.microsoft.azure.toolkit.intellij.java.sdk.utils.RuleConfigLoader;
import org.jetbrains.annotations.NotNull;

/**
 * Inspection to detect dynamic client creation in loops.
 */
public class DynamicClientCreationCheck extends LocalInspectionTool {

    @NotNull
    @Override
    public JavaElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new DynamicClientCreationVisitor(holder);
    }

    static class DynamicClientCreationVisitor extends JavaElementVisitor {

        private final ProblemsHolder holder;
        private static final RuleConfig RULE_CONFIG = RuleConfigLoader.getInstance()
            .getRuleConfig("DynamicClientCreationCheck");
        private static final boolean SKIP_WHOLE_RULE = RULE_CONFIG.skipRuleCheck();

        public DynamicClientCreationVisitor(ProblemsHolder holder) {
            this.holder = holder;
        }

        @Override
        public void visitForStatement(@NotNull PsiForStatement statement) {
            if (SKIP_WHOLE_RULE) return;

            PsiStatement body = statement.getBody();
            if (!(body instanceof PsiBlockStatement)) return;

            PsiCodeBlock codeBlock = ((PsiBlockStatement) body).getCodeBlock();
            for (PsiStatement blockChild : codeBlock.getStatements()) {
                if (blockChild instanceof PsiExpressionStatement) {
                    checkExpression(((PsiExpressionStatement) blockChild).getExpression());
                } else if (blockChild instanceof PsiDeclarationStatement) {
                    checkDeclaration((PsiDeclarationStatement) blockChild);
                }
            }
        }

        private void checkExpression(PsiExpression expression) {
            if (expression instanceof PsiAssignmentExpression) {
                PsiExpression rhs = ((PsiAssignmentExpression) expression).getRExpression();
                if (rhs instanceof PsiMethodCallExpression && isClientCreationMethod((PsiMethodCallExpression) rhs)) {
                    holder.registerProblem(rhs, RULE_CONFIG.getAntiPatternMessage());
                }
            }
        }

        private void checkDeclaration(PsiDeclarationStatement declaration) {
            for (PsiElement declaredElement : declaration.getDeclaredElements()) {
                if (declaredElement instanceof PsiLocalVariable) {
                    PsiExpression initializer = ((PsiLocalVariable) declaredElement).getInitializer();
                    if (initializer instanceof PsiMethodCallExpression && isClientCreationMethod((PsiMethodCallExpression) initializer)) {
                        holder.registerProblem(initializer, RULE_CONFIG.getAntiPatternMessage());
                    }
                }
            }
        }

        private boolean isClientCreationMethod(PsiMethodCallExpression methodCallExpression) {
            PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
            String methodName = methodExpression.getReferenceName();

            if (methodName == null || !RULE_CONFIG.getUsagesToCheck().contains(methodName)) {
                return false;
            }

            PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (qualifier == null || qualifier.getType() == null) {
                return false;
            }

            return qualifier.getType().getCanonicalText().startsWith(RuleConfig.AZURE_PACKAGE_NAME);
        }
    }
}
