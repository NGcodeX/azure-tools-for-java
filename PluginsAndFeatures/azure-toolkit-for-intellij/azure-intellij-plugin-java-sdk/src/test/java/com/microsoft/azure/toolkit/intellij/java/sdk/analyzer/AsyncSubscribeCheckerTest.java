// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.microsoft.azure.toolkit.intellij.java.sdk.models.RuleConfig;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AsyncSubscribeChecker} and derived classes.
 */
public class AsyncSubscribeCheckerTest {
    @Mock
    private ProblemsHolder mockHolder;

    @Mock
    private PsiMethodCallExpression mockMethodCallExpression;

    @BeforeEach
    public void setUp() {
        mockHolder = mock(ProblemsHolder.class);
        mockMethodCallExpression = mock(PsiMethodCallExpression.class);
    }

    @ParameterizedTest
    @MethodSource("provideTestCases")
    public void testWithParameterizedCases(String packageName, String mainMethodFound,
        int numOfInvocations, String followingMethod, String objectType, String expectedMessage) {
        JavaElementVisitor visitor = new UpdateCheckpointAsyncSubscribeChecker.UpdateCheckpointAsyncVisitor(mockHolder,
            true);
        setupMockMethodCall(packageName, mainMethodFound, numOfInvocations, followingMethod,
            objectType,
            expectedMessage);
        visitor.visitMethodCallExpression(mockMethodCallExpression);
        verify(mockHolder, times(numOfInvocations)).registerProblem(eq(mockMethodCallExpression),
            contains(expectedMessage));
    }

    private static Stream<Object[]> provideTestCases() {
        return Stream.of(
            new Object[] {"com.azure", "updateCheckpointAsync", 1,
                "subscribe", "EventBatchContext",
                "Instead of `subscribe()`, call `block()` or `block()` with timeout, or use the synchronous version `updateCheckpoint()`"},
            new Object[] {"com.azure", "updateCheckpointAsync", 0, "notSubscribe", "EventBatchContext",
                "Instead of `subscribe()`, call `block()` or `block()` with timeout, or use the synchronous " +
                    "version `updateCheckpoint()`"}
        );
    }

    private void setupMockMethodCall(String packageName, String mainMethodFound,
        int numOfInvocations, String followingMethod, String objectType, String expectedMessage) {
        PsiReferenceExpression mockReferenceExpression = mock(PsiReferenceExpression.class);
        PsiReferenceExpression parentReferenceExpression = mock(PsiReferenceExpression.class);
        PsiMethodCallExpression grandParentMethodCalLExpression = mock(PsiMethodCallExpression.class);
        PsiReferenceExpression mockQualifier = mock(PsiReferenceExpression.class);
        PsiParameter mockParameter = mock(PsiParameter.class);
        PsiClassType parameterType = mock(PsiClassType.class);
        PsiClass psiClass = mock(PsiClass.class);

        when(mockMethodCallExpression.getMethodExpression()).thenReturn(mockReferenceExpression);
        when(mockReferenceExpression.getReferenceName()).thenReturn(mainMethodFound);
        when(mockMethodCallExpression.getParent()).thenReturn(mockReferenceExpression);
        when(mockReferenceExpression.getParent()).thenReturn(grandParentMethodCalLExpression);
        when(grandParentMethodCalLExpression.getMethodExpression()).thenReturn(parentReferenceExpression);
        when(parentReferenceExpression.getReferenceName()).thenReturn(followingMethod);
        when(mockReferenceExpression.getQualifierExpression()).thenReturn(mockQualifier);
        when(mockQualifier.resolve()).thenReturn(mockParameter);
        when(mockParameter.getType()).thenReturn(parameterType);
        when(parameterType.getCanonicalText()).thenReturn(objectType);
        when(parameterType.resolve()).thenReturn(psiClass);
        when(psiClass.getQualifiedName()).thenReturn(RuleConfig.AZURE_PACKAGE_NAME);
    }
}