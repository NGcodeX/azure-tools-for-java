// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.microsoft.azure.toolkit.intellij.java.sdk.models.RuleConfig;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

import com.microsoft.azure.toolkit.intellij.java.sdk.analyzer.ServiceBusReceiveModeCheck.ServiceBusReceiveModeVisitor;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * This class contains the tests for the ServiceBusReceiveModeCheck class. It tests the visitDeclarationStatement method
 * of the ServiceBusReceiveModeVisitor class. It tests the method with different combinations of the receiveMode and
 * prefetchCount methods.
 */
public class ServiceBusReceiveModeCheckTest {

    @Mock
    private ProblemsHolder mockHolder;

    @Mock
    private JavaElementVisitor mockVisitor;

    @Mock
    private PsiDeclarationStatement mockDeclarationStatement;

    @Mock
    private PsiElement prefetchCountMethod;

    @BeforeEach
    public void setUp() {
        mockHolder = mock(ProblemsHolder.class);
        mockVisitor = new ServiceBusReceiveModeCheck.ServiceBusReceiveModeVisitor(mockHolder, true);
        mockDeclarationStatement = mock(PsiDeclarationStatement.class);
        prefetchCountMethod = mock(PsiElement.class);
    }

    @ParameterizedTest
    @MethodSource("provideTestCases")
    public void testServiceBusReceiveModeCheck(TestCase testCase) {
        setupPrefetchMethod(testCase.clientName, testCase.methodFoundOne, testCase.methodFoundTwo,
            testCase.numOfInvocations,
            testCase.prefetchCountValue);
        mockVisitor.visitDeclarationStatement(mockDeclarationStatement);
        verify(mockHolder, times(testCase.numOfInvocations)).registerProblem(eq(prefetchCountMethod), eq(
            "A high prefetch value in PEEK_LOCK detected. We recommend a prefetch value of 0 or 1 for efficient message retrieval."));
    }

    private void setupPrefetchMethod(String clientName, String methodFoundOne, String methodFoundTwo,
        int numOfInvocations, String prefetchCountValue) {
        PsiVariable declaredElement = mock(PsiVariable.class);
        PsiElement[] declaredElements = new PsiElement[] {declaredElement};

        PsiType clientType = mock(PsiType.class);
        PsiMethodCallExpression initializer = mock(PsiMethodCallExpression.class);

        PsiReferenceExpression expressionOne = mock(PsiReferenceExpression.class);
        PsiMethodCallExpression qualifierOne = mock(PsiMethodCallExpression.class);
        PsiReferenceExpression methodExpressionOne = mock(PsiReferenceExpression.class);

        PsiExpression receiveModePeekLockParameter = mock(PsiExpression.class);
        PsiReferenceExpression prefetchCountParameter = mock(PsiReferenceExpression.class);
        PsiExpressionList methodArgumentList = mock(PsiExpressionList.class);
        PsiExpression[] methodArguments = new PsiExpression[] {prefetchCountParameter, receiveModePeekLockParameter};

        PsiMethodCallExpression qualifierTwo = mock(PsiMethodCallExpression.class);
        PsiReferenceExpression methodExpressionTwo = mock(PsiReferenceExpression.class);

        prefetchCountMethod = mock(PsiElement.class);

        when(mockDeclarationStatement.getDeclaredElements()).thenReturn(declaredElements);

        when(declaredElement.getType()).thenReturn(clientType);
        when(declaredElement.getInitializer()).thenReturn(initializer);
        when(clientType.getCanonicalText()).thenReturn(RuleConfig.AZURE_PACKAGE_NAME);
        when(clientType.getPresentableText()).thenReturn(clientName);

        when(initializer.getMethodExpression()).thenReturn(expressionOne);
        when(expressionOne.getQualifierExpression()).thenReturn(qualifierOne);
        when(qualifierOne.getMethodExpression()).thenReturn(methodExpressionOne);
        when(methodExpressionOne.getReferenceName()).thenReturn(methodFoundOne);

        when(qualifierOne.getArgumentList()).thenReturn(methodArgumentList);
        when(methodArgumentList.getExpressions()).thenReturn(methodArguments);
        when(receiveModePeekLockParameter.getText()).thenReturn("PEEK_LOCK");

        when(methodExpressionOne.getQualifierExpression()).thenReturn(qualifierTwo);
        when(qualifierTwo.getMethodExpression()).thenReturn(methodExpressionTwo);
        when(methodExpressionTwo.getReferenceName()).thenReturn(methodFoundTwo);

        when(qualifierTwo.getArgumentList()).thenReturn(methodArgumentList);
        when(methodArgumentList.getExpressions()).thenReturn(methodArguments);
        when(prefetchCountParameter.getText()).thenReturn(prefetchCountValue);

        when(methodExpressionTwo.getReferenceNameElement()).thenReturn(prefetchCountMethod);

        if (!"receiveMode".equals(methodFoundOne) || !"prefetchCount".equals(methodFoundTwo)) {
            when(methodExpressionTwo.getQualifierExpression()).thenReturn(null);
        }

    }

    private static Stream<TestCase> provideTestCases() {
        return Stream.of(
            new TestCase("ServiceBusReceiverClient", "receiveMode", "prefetchCount", 1, "100"),
            new TestCase("ServiceBusReceiverClient", "receiveMode", "prefetchCount", 0, "1"),
            new TestCase("ServiceBusReceiverClient", "notreceiveMode", "prefetchCount", 0, "100"),
            new TestCase("ServiceBusReceiverClient", "receiveMode", "noprefetchCount", 0, "100"),
            new TestCase("servicebus", "notreceiveMode", "noprefetchCount", 0, "100"),
            new TestCase("notservicebus", "notreceiveMode", "noprefetchCount", 0, "100")
        );
    }

    static class TestCase {
        String clientName;
        String methodFoundOne;
        String methodFoundTwo;
        int numOfInvocations;
        String prefetchCountValue;

        TestCase(String clientName, String methodFoundOne, String methodFoundTwo, int numOfInvocations,
            String prefetchCountValue) {
            this.clientName = clientName;
            this.methodFoundOne = methodFoundOne;
            this.methodFoundTwo = methodFoundTwo;
            this.numOfInvocations = numOfInvocations;
            this.prefetchCountValue = prefetchCountValue;
        }
    }
}