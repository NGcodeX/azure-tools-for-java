// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTypesUtil;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * This class is used to test the StorageUploadWithoutLengthCheck class.
 * It tests the visitor method to check if the upload methods are being called without a 'length' parameter of type 'long'.
 *
 * These are examples of situations where the visitor method should register a problem:
 * 1. @ServiceMethod(returns = ReturnType.SINGLE)
 *     public void upload(InputStream data) {
 *         uploadWithResponse(new BlobParallelUploadOptions(data), null, null);
 *     }
 *
 * 2. uploadWithResponse(new BlobParallelUploadOptions(data).setRequestConditions(blobRequestConditions), null, Context.NONE);
 *
 * 3. upload(data, false);
 */
public class StorageUploadWithoutLengthCheckTest {

    @Mock
    private ProblemsHolder mockHolder;
    @Mock
    private JavaRecursiveElementWalkingVisitor mockVisitor;
    @Mock
    private PsiMethodCallExpression mockExpression;

    @BeforeEach
    public void setup() {
        mockHolder = mock(ProblemsHolder.class);
        mockVisitor = new StorageUploadWithoutLengthCheck.StorageUploadVisitor(mockHolder);
        mockExpression = mock(PsiMethodCallExpression.class);
    }

    @ParameterizedTest
    @MethodSource("provideTestCases")
    public void testStorageUploadWithoutLengthCheck(TestCase testCase) {
        setupStorageCall(testCase.methodName, testCase.numberOfInvocations);
        mockVisitor.visitMethodCallExpression(mockExpression);
        verify(mockHolder, times(testCase.numberOfInvocations)).registerProblem(eq(mockExpression), contains("Azure " +
            "Storage upload API without length parameter detected"));
    }

    private void setupStorageCall(String methodName, int numberOfInvocations) {
        mockExpression = mock(PsiMethodCallExpression.class);
        PsiReferenceExpression mockReferenceExpression = mock(PsiReferenceExpression.class);
        PsiExpression mockQualifierExpression = mock(PsiExpression.class);
        PsiType mockPsiType = mock(PsiType.class);
        PsiClassType qualifierType = mock(PsiClassType.class);
        PsiExpressionList mockArgumentList = mock(PsiExpressionList.class);
        PsiClass mockContainingClass = mock(PsiClass.class);

        when(mockExpression.getMethodExpression()).thenReturn(mockReferenceExpression);

        when(mockReferenceExpression.getQualifierExpression()).thenReturn(mockQualifierExpression);
        when(mockQualifierExpression.getType()).thenReturn(qualifierType);
        when(qualifierType.resolve()).thenReturn(mockContainingClass);

        when(mockContainingClass.getQualifiedName()).thenReturn("com.azure.storage.blob.BlobAsyncClient");

        when(mockReferenceExpression.getReferenceName()).thenReturn(methodName);

        when(mockExpression.getArgumentList()).thenReturn(mockArgumentList);
        PsiExpression[] mockArguments = new PsiExpression[numberOfInvocations];
        for (int i = 0; i < numberOfInvocations; i++) {
            mockArguments[i] = mock(PsiExpression.class);
        }
        when(mockArgumentList.getExpressions()).thenReturn(mockArguments);
    }


    private static Stream<TestCase> provideTestCases() {
        return Stream.of(
            new TestCase("upload", 1),
            new TestCase("notInList", 0)
        );
    }

    private static class TestCase {
        String methodName;
        int numberOfInvocations;

        TestCase(String methodName, int numberOfInvocations) {
            this.methodName = methodName;
            this.numberOfInvocations = numberOfInvocations;
        }
    }
}

