// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.microsoft.azure.toolkit.intellij.java.sdk.models.RuleConfig;
import java.util.Collections;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DetectDiscouragedAPIUsageCheck} and derived classes.
 */
public class DetectDiscouragedAPIUsageCheckTest {

    @Mock
    private ProblemsHolder mockHolder;

    @Mock
    private JavaElementVisitor mockVisitor;

    @Mock
    private PsiMethodCallExpression methodCallExpression;

    @Mock
    private PsiElement problemElement;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        mockHolder = mock(ProblemsHolder.class);
        methodCallExpression = mock(PsiMethodCallExpression.class);
    }

    @ParameterizedTest
    @MethodSource("provideTestCases")
    public void detectsDiscouragedAPIUsage(TestCase testCase) {
        setupMockAPI(testCase.methodToCheck, testCase.numOfInvocations, testCase.packageName,
            testCase.suggestionMessage);
        mockVisitor = createVisitor(testCase.ruleConfig);
        mockVisitor.visitElement(methodCallExpression);
        verify(mockHolder, times(testCase.numOfInvocations)).registerProblem(eq(problemElement),
            eq(testCase.suggestionMessage));
    }

    private JavaElementVisitor createVisitor(RuleConfig ruleConfig) {
        return new DetectDiscouragedAPIUsageCheck.DetectDiscouragedAPIUsageVisitor(mockHolder, ruleConfig);
    }

    private static RuleConfig getGetCompletionsConfig() {
        RuleConfig getCompletionsConfig = mock(RuleConfig.class);
        when(getCompletionsConfig.getUsagesToCheck()).thenReturn(Collections.singletonList("getCompletions"));
        when(getCompletionsConfig.getScopeToCheck()).thenReturn(Collections.singletonList("com.azure.ai.openai"));
        when(getCompletionsConfig.getAntiPatternMessage()).thenReturn(
            "getCompletions API detected. Use the getChatCompletions API instead.");
        return getCompletionsConfig;
    }

    private static RuleConfig getConnectionStringConfig() {
        RuleConfig connectionStringConfig = mock(RuleConfig.class);
        when(connectionStringConfig.getUsagesToCheck()).thenReturn(Collections.singletonList("connectionString"));
        when(connectionStringConfig.getScopeToCheck()).thenReturn(Collections.singletonList("com.azure"));
        when(connectionStringConfig.getAntiPatternMessage()).thenReturn(
            "Connection String detected. Use DefaultAzureCredential for Azure service client authentication instead if the service client supports Token Credential (Entra ID Authentication).");
        return connectionStringConfig;
    }

    private void setupMockAPI(String methodToCheck, int numOfInvocations, String packageName,
        String suggestionMessage) {
        methodCallExpression = mock(PsiMethodCallExpression.class);
        PsiReferenceExpression methodExpression = mock(PsiReferenceExpression.class);
        PsiMethod resolvedMethod = mock(PsiMethod.class);
        PsiClass containingClass = mock(PsiClass.class);
        problemElement = mock(PsiElement.class);

        when(methodCallExpression.getMethodExpression()).thenReturn(methodExpression);
        when(methodExpression.resolve()).thenReturn(resolvedMethod);
        when(resolvedMethod.getContainingClass()).thenReturn(containingClass);
        when(resolvedMethod.getName()).thenReturn(methodToCheck);
        when(containingClass.getQualifiedName()).thenReturn(packageName);
        when(methodExpression.getReferenceNameElement()).thenReturn(problemElement);
    }

    private static Stream<TestCase> provideTestCases() {
        return Stream.of(
            new TestCase(getConnectionStringConfig(), "connectionString", "com.azure", "Connection String detected. " +
                "Use DefaultAzureCredential for Azure service client authentication instead if the service client supports Token Credential (Entra ID Authentication).",
                1),
            new TestCase(getGetCompletionsConfig(), "getCompletions", "com.azure.ai.openai", "getCompletions API " +
                "detected. Use the getChatCompletions API instead.", 1),
            new TestCase(getConnectionStringConfig(), "allowedMethod", "com.azure", "", 0),
            new TestCase(getConnectionStringConfig(), "connectionString", "com.microsoft.azure", "", 0),
            new TestCase(getGetCompletionsConfig(), "getCompletions", "com.azure.other", "", 0)
        );
    }

    private static class TestCase {
        String methodToCheck;
        String packageName;
        String suggestionMessage;
        int numOfInvocations;
        RuleConfig ruleConfig;

        TestCase(RuleConfig ruleConfig, String methodToCheck, String packageName, String suggestionMessage,
            int numOfInvocations) {
            this.methodToCheck = methodToCheck;
            this.packageName = packageName;
            this.suggestionMessage = suggestionMessage;
            this.numOfInvocations = numOfInvocations;
            this.ruleConfig = ruleConfig;
        }
    }
}