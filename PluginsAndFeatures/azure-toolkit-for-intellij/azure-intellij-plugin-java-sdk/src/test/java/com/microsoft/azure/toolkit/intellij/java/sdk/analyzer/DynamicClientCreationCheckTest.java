// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiMethodCallExpression;
import com.microsoft.azure.toolkit.intellij.java.sdk.analyzer.DynamicClientCreationCheck.DynamicClientCreationVisitor;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
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

/***
 * Tests for {@link DynamicClientCreationCheck}
 */
public class DynamicClientCreationCheckTest {

    @Mock
    private ProblemsHolder mockHolder;

    @Mock
    private JavaElementVisitor mockVisitor;

    @Mock
    private PsiForStatement mockElement;

    @Mock
    private PsiMethodCallExpression mockMethodCallExpression;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        mockHolder = mock(ProblemsHolder.class);
        mockVisitor = new DynamicClientCreationVisitor(mockHolder);
        mockElement = mock(PsiForStatement.class);
        mockMethodCallExpression = mock(PsiMethodCallExpression.class);
    }

    @ParameterizedTest
    @MethodSource("provideTestCases")
    public void testDynamicClientCreation(TestCase testCase) {
        if (testCase.isAssignment) {
            setupAssignmentExpression(testCase.methodName, testCase.packageName, testCase.numOfInvocations);
            mockVisitor.visitForStatement(mockElement);

            verify(mockHolder, times(testCase.numOfInvocations)).registerProblem(eq(mockMethodCallExpression),
                contains("Dynamic client creation detected. Create a single client instance and reuse it instead."));
        } else {
            setupWithDeclarationStatement(testCase.methodName, testCase.packageName, testCase.numOfInvocations);
            mockVisitor.visitForStatement(mockElement);

            verify(mockHolder, times(testCase.numOfInvocations)).registerProblem(eq(mockMethodCallExpression), contains("Dynamic " +
                "client creation detected. Create a single client instance and reuse it instead."));
        }
    }

    private void setupAssignmentExpression(String methodName, String packageName, int numOfInvocations) {
        PsiStatement statement = mock(PsiStatement.class);
        PsiBlockStatement body = mock(PsiBlockStatement.class);
        PsiCodeBlock codeBlock = mock(PsiCodeBlock.class);
        PsiExpressionStatement blockChild = mock(PsiExpressionStatement.class);
        PsiStatement[] blockStatements = new PsiStatement[] {blockChild};

        PsiAssignmentExpression expression = mock(PsiAssignmentExpression.class);
        mockMethodCallExpression = mock(PsiMethodCallExpression.class);

        PsiReferenceExpression methodExpression = mock(PsiReferenceExpression.class);
        PsiExpression qualifierExpression = mock(PsiExpression.class);
        PsiType type = mock(PsiType.class);

        when(mockElement.getBody()).thenReturn(body);
        when(body.getCodeBlock()).thenReturn(codeBlock);
        when(codeBlock.getStatements()).thenReturn(blockStatements);

        when(blockChild.getExpression()).thenReturn(expression);
        when(expression.getRExpression()).thenReturn(mockMethodCallExpression);

        when(mockMethodCallExpression.getMethodExpression()).thenReturn(methodExpression);
        when(methodExpression.getReferenceName()).thenReturn(methodName);
        when(methodExpression.getQualifierExpression()).thenReturn(qualifierExpression);
        when(qualifierExpression.getType()).thenReturn(type);
        when(qualifierExpression.getType().getCanonicalText()).thenReturn(packageName);
    }

    private void setupWithDeclarationStatement(String methodName, String packageName, int numOfInvocations) {
        PsiStatement statement = mock(PsiStatement.class);
        PsiBlockStatement body = mock(PsiBlockStatement.class);
        PsiCodeBlock codeBlock = mock(PsiCodeBlock.class);
        PsiDeclarationStatement blockChild = mock(PsiDeclarationStatement.class);
        PsiStatement[] blockStatements = new PsiStatement[] {blockChild};

        PsiLocalVariable declaredElement = mock(PsiLocalVariable.class);
        PsiElement[] declaredElements = new PsiElement[] {declaredElement};
        mockMethodCallExpression = mock(PsiMethodCallExpression.class);

        PsiReferenceExpression methodExpression = mock(PsiReferenceExpression.class);
        PsiExpression qualifierExpression = mock(PsiExpression.class);
        PsiType type = mock(PsiType.class);

        when(mockElement.getBody()).thenReturn(body);
        when(body.getCodeBlock()).thenReturn(codeBlock);
        when(codeBlock.getStatements()).thenReturn(blockStatements);

        when(blockChild.getDeclaredElements()).thenReturn(declaredElements);
        when(declaredElement.getInitializer()).thenReturn(mockMethodCallExpression);

        when(mockMethodCallExpression.getMethodExpression()).thenReturn(methodExpression);
        when(methodExpression.getReferenceName()).thenReturn(methodName);
        when(methodExpression.getQualifierExpression()).thenReturn(qualifierExpression);
        when(qualifierExpression.getType()).thenReturn(type);
        when(qualifierExpression.getType().getCanonicalText()).thenReturn(packageName);
    }

    private static Stream<TestCase> provideTestCases() {
        return Stream.of(
            new TestCase("buildClient", "com.azure.", 1, true),
            new TestCase("buildClient", "com.azure.", 1, false),
            new TestCase("buildClient", "com.Notazure.", 0, true),
            new TestCase("buildClient", "com.Notazure.", 0, false),
            new TestCase("NotbuildClient", "com.azure.", 0, true),
            new TestCase("NotbuildClient", "com.azure.", 0, false)
        );
    }

    private static class TestCase {
        String methodName;
        String packageName;
        int numOfInvocations;
        boolean isAssignment;

        TestCase(String methodName, String packageName, int numOfInvocations, boolean isAssignment) {
            this.methodName = methodName;
            this.packageName = packageName;
            this.numOfInvocations = numOfInvocations;
            this.isAssignment = isAssignment;
        }
    }
}