// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.microsoft.azure.toolkit.intellij.java.sdk.models.RuleConfig;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Abstract base class to check for the use of discouraged APIs in the code.
 */
public abstract class DetectDiscouragedAPIUsageCheck extends LocalInspectionTool {

    /**
     * Provides the specific RuleConfig for the subclass context.
     *
     * @return RuleConfig for the subclass
     */
    protected abstract RuleConfig getRuleConfig();

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        RuleConfig ruleConfig = getRuleConfig();

        if (ruleConfig.skipRuleCheck()) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }

        return new DetectDiscouragedAPIUsageVisitor(holder, ruleConfig);
    }

    static class DetectDiscouragedAPIUsageVisitor extends JavaElementVisitor {
        private final ProblemsHolder holder;
        private final RuleConfig ruleConfig;

        DetectDiscouragedAPIUsageVisitor(ProblemsHolder holder, RuleConfig ruleConfig) {
            this.holder = holder;
            this.ruleConfig = ruleConfig;
        }

        @Override
        public void visitElement(@NotNull PsiElement element) {
            super.visitElement(element);

            // Ensure the element is a method call
            if (!(element instanceof PsiMethodCallExpression methodCallExpression)) {
                return;
            }

            // Resolve the method being called
            PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
            PsiElement resolvedMethod = methodExpression.resolve();
            if (!(resolvedMethod instanceof PsiMethod method)) {
                return;
            }

            // Get the containing class of the method
            PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }

            // Get qualified name of the containing class
            String classQualifiedName = containingClass.getQualifiedName();
            if (classQualifiedName == null) {
                return;
            }

            // Check if the method is a discouraged API
            String methodName = method.getName();
            if (!ruleConfig.getUsagesToCheck().contains(methodName)) {
                return;
            }

            // Verify package name and scope
            if (!classQualifiedName.startsWith(RuleConfig.AZURE_PACKAGE_NAME)) {
                return;
            }

            Set<String> scopesToCheck = new HashSet<>(ruleConfig.getScopeToCheck());
            if (scopesToCheck.stream().noneMatch(classQualifiedName::startsWith)) {
                return;
            }

            // Register a problem for the discouraged API usage
            PsiElement problemElement = methodExpression.getReferenceNameElement();
            if (problemElement != null) {
                holder.registerProblem(problemElement, ruleConfig.getAntiPatternMessage());
            }
        }
    }
}

