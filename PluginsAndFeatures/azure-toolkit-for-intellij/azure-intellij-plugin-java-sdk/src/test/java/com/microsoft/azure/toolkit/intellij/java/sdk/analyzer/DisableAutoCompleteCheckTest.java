// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.microsoft.azure.toolkit.intellij.java.sdk.analyzer.DisableAutoCompleteCheck.DisableAutoCompleteVisitor;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * This class is used to test the DisableAutoCompleteCheck class. It tests the visitDeclarationStatement method of the
 * DisableAutoCompleteVisitor class. Use of AC refers to the auto-complete feature.
 */

public class DisableAutoCompleteCheckTest {

    @Mock
    private ProblemsHolder mockHolder;

    @Mock
    private JavaElementVisitor mockVisitor;

    @Mock
    private PsiDeclarationStatement mockDeclarationStatement;

    @Mock
    private PsiMethodCallExpression initializer;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mockHolder = mock(ProblemsHolder.class);
        mockVisitor = new DisableAutoCompleteVisitor(mockHolder);
        mockDeclarationStatement = mock(PsiDeclarationStatement.class);
    }

    @ParameterizedTest
    @MethodSource("provideTestCases")
    public void testDisableAutoCompleteCheck(TestCase testCase) {
        setupMockMethodCall(testCase.packageName, testCase.clientName, testCase.numOfInvocations,
            testCase.methodFound);
        mockVisitor.visitDeclarationStatement(mockDeclarationStatement);
        verify(mockHolder, times(testCase.numOfInvocations)).registerProblem(eq(initializer), contains(
            "Auto-complete enabled by default. Use the disableAutoComplete() API call to prevent automatic message completion."));

    }

    private void setupMockMethodCall(String packageName, String clientName, int numOfInvocations,
        String methodFound) {
        PsiVariable declaredElement = mock(PsiVariable.class);
        PsiElement[] declaredElements = new PsiElement[] {declaredElement};

        PsiType clientType = mock(PsiType.class);
        initializer = mock(PsiMethodCallExpression.class);

        PsiReferenceExpression expression = mock(PsiReferenceExpression.class);
        PsiMethodCallExpression qualifier = mock(PsiMethodCallExpression.class);
        PsiMethodCallExpression finalExpression = mock(PsiMethodCallExpression.class);

        when(mockDeclarationStatement.getDeclaredElements()).thenReturn(declaredElements);
        when(declaredElement.getType()).thenReturn(clientType);
        when(declaredElement.getInitializer()).thenReturn(initializer);
        when(clientType.getCanonicalText()).thenReturn(packageName);
        when(clientType.getPresentableText()).thenReturn(clientName);

        when(initializer.getMethodExpression()).thenReturn(expression);
        when(expression.getQualifierExpression()).thenReturn(qualifier);

        when(qualifier.getMethodExpression()).thenReturn(expression);
        when(expression.getQualifierExpression()).thenReturn(finalExpression);
        when(expression.getReferenceName()).thenReturn(methodFound);

        when(finalExpression.getMethodExpression()).thenReturn(expression);

        if (!"disableAutoComplete".equals(methodFound)) {
            when(expression.getQualifierExpression()).thenReturn(null);
        }
    }

    private static Stream<TestCase> provideTestCases() {
        return Stream.of(
            new TestCase("com.azure", "ServiceBusReceiverClient", 1, "notDisableAutoComplete"),
            new TestCase("com.azure", "ServiceBusProcessorClient", 1, "notDisableAutoComplete"),
            new TestCase("com.azure", "ServiceBusRuleManagerClient", 0, "notDisableAutoComplete"),
            new TestCase("com.azure", "ServiceBusReceiverClient", 0, "disableAutoComplete"),
            new TestCase("com.microsoft.azure", "ServiceBusReceiverClient", 0, "disableAutoComplete")
        );
    }

    private static class TestCase {
        String packageName;
        String clientName;
        int numOfInvocations;
        String methodFound;

        TestCase(String packageName, String clientName, int numOfInvocations, String methodFound) {
            this.packageName = packageName;
            this.clientName = clientName;
            this.numOfInvocations = numOfInvocations;
            this.methodFound = methodFound;
        }
    }
}