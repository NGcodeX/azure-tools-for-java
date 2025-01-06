// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.microsoft.azure.toolkit.intellij.java.sdk.models.RuleConfig;
import java.util.List;

/**
 * Abstract base class for checking the usage of the following method to be ''subscribe'' in provided context.
 */
public abstract class AsyncSubscribeChecker extends LocalInspectionTool {
    /**
     * Checks if the method call is following a specific method call like `subscribe`.
     *
     * @param expression The method call expression to analyze.
     *
     * @return True if the method call is following a `subscribe` method call, false otherwise.
     */
    protected static boolean isFollowingMethodSubscribe(PsiMethodCallExpression expression) {
        // Check if the parent element is a method call expression
        if (!(expression.getParent() instanceof PsiReferenceExpression reference)) {
            return false;
        }

        // Check if the grandparent element is a method call expression
        PsiElement grandParent = reference.getParent();
        if (!(grandParent instanceof PsiMethodCallExpression parentCall)) {
            return false;
        }

        // Check if the parent method call's name is "subscribe"
        return "subscribe".equals(parentCall.getMethodExpression().getReferenceName());
    }

    /**
     * This method checks if the method call is made on an object from the provided Azure SDK context.
     *
     * @param expression The method call expression to analyze.
     * @param scopeToCheck The list of class contexts to check.
     *
     * @return True if the method call is made on an object in the provided Azure SDK context, false otherwise.
     */
    protected static boolean isCalledInProvidedContext(PsiMethodCallExpression expression, List<String> scopeToCheck) {
        // Get the qualifier expression from the method call expression
        PsiExpression qualifier = expression.getMethodExpression().getQualifierExpression();
        if (!(qualifier instanceof PsiReferenceExpression)) {
            return false;
        }

        // Resolve the qualifier element
        PsiElement resolvedElement = ((PsiReferenceExpression) qualifier).resolve();
        if (!(resolvedElement instanceof PsiParameter)) {
            return false;
        }

        // Check the parameter type
        PsiParameter parameter = (PsiParameter) resolvedElement;
        PsiType parameterType = parameter.getType();

        // Verify if the parameter type is within the provided context
        if (!(parameterType instanceof PsiClassType)) {
            return false;
        }

        PsiClassType classType = (PsiClassType) parameterType;
        PsiClass psiClass = classType.resolve();
        if (psiClass == null) {
            return false;
        }

        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null) {
            return false;
        }

        // Check if the qualified name matches the Azure SDK context
        boolean isInProvidedScope = scopeToCheck.contains(parameterType.getCanonicalText());
        boolean isAzureSDKContext = qualifiedName.startsWith(RuleConfig.AZURE_PACKAGE_NAME);

        return isInProvidedScope && isAzureSDKContext;
    }
}
