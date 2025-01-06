// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GetSyncPollerOnPollerFluxCheck.
 */
public class GetSyncPollerOnPollerFluxCheckTest {

    @Mock
    private ProblemsHolder mockHolder;

    @Mock
    private JavaElementVisitor mockVisitor;

    @Mock
    private PsiMethodCallExpression mockElement;

    @BeforeEach
    public void setup() {
        mockHolder = mock(ProblemsHolder.class);
        mockVisitor = new GetSyncPollerOnPollerFluxCheck().new GetSyncPollerOnPollerFluxVisitor(mockHolder);
        mockElement = mock(PsiMethodCallExpression.class);
    }

    @ParameterizedTest
    @MethodSource("testCases")
    public void testGetSyncPollerOnPollerFluxCheck(TestCase testCase) {
        mockMethodExpression(testCase.methodName, testCase.className, testCase.numberOfInvocations);
        mockVisitor.visitMethodCallExpression(mockElement);
        verify(mockHolder, times(testCase.numberOfInvocations)).registerProblem(mockElement, "Use of getSyncPoller() on a " +
            "PollerFlux detected. Directly use SyncPoller to handle synchronous polling tasks.");
    }

    private static Stream<TestCase> testCases() {
        return Stream.of(
            new TestCase("getSyncPoller", "com.azure.core.util.polling.PollerFlux", 1),
            new TestCase("getAnotherMethod", "com.azure.core.util.polling.PollerFlux", 0),
            new TestCase("getSyncPoller", "com.azure.core.util.polling.DifferentClassName", 0),
            new TestCase("getSyncPoller", null, 0)
        );
    }

    private void mockMethodExpression(String methodName, String className, int numberOfInvocations) {
        PsiReferenceExpression referenceExpression = mock(PsiReferenceExpression.class);
        PsiExpression expression = mock(PsiExpression.class);
        PsiType type = mock(PsiType.class);
        PsiClass containingClass = mock(PsiClass.class);
        PsiTreeUtil mockTreeUtil = mock(PsiTreeUtil.class);

        when(mockElement.getMethodExpression()).thenReturn(referenceExpression);
        when(referenceExpression.getReferenceName()).thenReturn(methodName);
        when(referenceExpression.getQualifierExpression()).thenReturn(expression);
        when(expression.getType()).thenReturn(type);
        when(type.getCanonicalText()).thenReturn(className);
        when(mockTreeUtil.getParentOfType(mockElement, PsiClass.class)).thenReturn(containingClass);
        when(containingClass.getQualifiedName()).thenReturn(className);
    }

    private static class TestCase {
        String methodName;
        String className;
        int numberOfInvocations;

        TestCase(String methodName, String className, int numberOfInvocations) {
            this.methodName = methodName;
            this.className = className;
            this.numberOfInvocations = numberOfInvocations;
        }
    }
}