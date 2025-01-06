// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiDoWhileStatement;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiWhileStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.microsoft.azure.toolkit.intellij.java.sdk.analyzer.SingleOperationInLoopCheck.SingleOperationInLoopVisitor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * This class is used to test the SingleOperationInLoopCheck class. The SingleOperationInLoopCheck is an inspection to
 * check if there is a single Azure client operation inside a loop. A single Azure client operation is defined as a
 * method call on a class that is part of the Azure SDK. If a single Azure client operation is found inside a loop, a
 * problem will be registered.
 * <p>
 * THis is an example of a situation where the inspection should register a problem:
 * <p>
 * 1. With a single PsiDeclarationStatement inside a while loop // While loop int i = 0; while (i < 10) {
 * <p>
 * BlobAsyncClient blobAsyncClient = new BlobClientBuilder()
 * .endpoint("https://<your-storage-account-name>.blob.core.windows.net") .sasToken("<your-sas-token>")
 * .containerName("<your-container-name>") .blobName("<your-blob-name>") .buildAsyncClient();
 * <p>
 * i++; }
 * <p>
 * 2. With a single PsiExpressionStatement inside a for loop for (String documentPath : documentPaths) {
 * <p>
 * blobAsyncClient.uploadFromFile(documentPath) .doOnSuccess(response -> System.out.println("Blob uploaded successfully
 * in enhanced for loop.")) .subscribe(); }
 */
public class SingleOperationInLoopCheckTest {

    @Mock
    private ProblemsHolder mockHolder;
    private JavaElementVisitor mockVisitor;
    private PsiMethodCallExpression initializer;
    private PsiMethodCallExpression expression;

    @BeforeEach
    public void setup() {
        mockHolder = mock(ProblemsHolder.class);
        mockVisitor = createVisitor();
        initializer = mock(PsiMethodCallExpression.class);
        expression = mock(PsiMethodCallExpression.class);
    }

    @ParameterizedTest
    @MethodSource("provideTestCases")
    public void testSingleOperationInLoop(TestCase testCase) {
        if (testCase.isDeclaration) {
            setupWithSinglePsiDeclarationStatement(testCase.loopStatement, testCase.packageName,
                testCase.numberOfInvocations, testCase.methodName);
            verify(mockHolder, times(testCase.numberOfInvocations)).registerProblem(Mockito.eq(initializer),
                Mockito.contains(
                    "Single operation found in loop. This SDK provides a batch operation API, use it to perform multiple actions in a single request: " +
                        testCase.methodName + "Batch"));
        } else {
            setupWithSinglePsiExpressionStatement(testCase.loopStatement, testCase.packageName,
                testCase.numberOfInvocations, testCase.methodName);
            verify(mockHolder, times(testCase.numberOfInvocations)).registerProblem(Mockito.eq(expression),
                Mockito.contains(
                    "Single operation found in loop. This SDK provides a batch operation API, use it to perform multiple actions in a single request: " +
                        testCase.methodName + "Batch"));
        }
    }

    private JavaElementVisitor createVisitor() {
        boolean isOnTheFly = true;
        SingleOperationInLoopVisitor visitor = new SingleOperationInLoopVisitor(mockHolder, isOnTheFly);
        return visitor;
    }

    private void setupWithSinglePsiExpressionStatement(PsiStatement loopStatement, String packageName,
        int numberOfInvocations, String methodName) {
        PsiBlockStatement loopBody = mock(PsiBlockStatement.class);
        PsiCodeBlock codeBlock = mock(PsiCodeBlock.class);
        expression = mock(PsiMethodCallExpression.class);
        PsiTreeUtil treeUtil = mock(PsiTreeUtil.class);
        PsiClass containingClass = mock(PsiClass.class);
        PsiReferenceExpression referenceExpression = mock(PsiReferenceExpression.class);
        PsiExpressionStatement mockStatement = mock(PsiExpressionStatement.class);
        PsiStatement[] statements = new PsiStatement[] {mockStatement};

        when(mockStatement.getExpression()).thenReturn(expression);
        when(loopBody.getCodeBlock()).thenReturn(codeBlock);
        when(codeBlock.getStatements()).thenReturn(statements);
        when(treeUtil.getParentOfType(expression, PsiClass.class)).thenReturn(containingClass);
        when(containingClass.getQualifiedName()).thenReturn(packageName);
        when(expression.getMethodExpression()).thenReturn(referenceExpression);
        when(referenceExpression.getReferenceName()).thenReturn(methodName);

        if (loopStatement instanceof PsiForStatement) {
            when(((PsiForStatement) loopStatement).getBody()).thenReturn(loopBody);
            mockVisitor.visitForStatement((PsiForStatement) loopStatement);
        } else if (loopStatement instanceof PsiForeachStatement) {
            when(((PsiForeachStatement) loopStatement).getBody()).thenReturn(loopBody);
            mockVisitor.visitForeachStatement((PsiForeachStatement) loopStatement);
        } else if (loopStatement instanceof PsiWhileStatement) {
            when(((PsiWhileStatement) loopStatement).getBody()).thenReturn(loopBody);
            mockVisitor.visitWhileStatement((PsiWhileStatement) loopStatement);
        } else if (loopStatement instanceof PsiDoWhileStatement) {
            when(((PsiDoWhileStatement) loopStatement).getBody()).thenReturn(loopBody);
            mockVisitor.visitDoWhileStatement((PsiDoWhileStatement) loopStatement);
        }
    }

    private void setupWithSinglePsiDeclarationStatement(PsiStatement loopStatement, String packageName,
        int numberOfInvocations, String methodName) {
        PsiBlockStatement loopBody = mock(PsiBlockStatement.class);
        PsiCodeBlock codeBlock = mock(PsiCodeBlock.class);
        PsiVariable element = mock(PsiVariable.class);
        PsiElement[] elements = new PsiElement[] {element};
        initializer = mock(PsiMethodCallExpression.class);
        PsiTreeUtil treeUtil = mock(PsiTreeUtil.class);
        PsiClass containingClass = mock(PsiClass.class);
        PsiReferenceExpression referenceExpression = mock(PsiReferenceExpression.class);
        PsiDeclarationStatement mockStatement = mock(PsiDeclarationStatement.class);
        PsiStatement[] statements = new PsiStatement[] {mockStatement};

        when(mockStatement.getDeclaredElements()).thenReturn(elements);
        when(loopBody.getCodeBlock()).thenReturn(codeBlock);
        when(codeBlock.getStatements()).thenReturn(statements);
        when(element.getInitializer()).thenReturn(initializer);
        when(treeUtil.getParentOfType(initializer, PsiClass.class)).thenReturn(containingClass);
        when(containingClass.getQualifiedName()).thenReturn(packageName);
        when(initializer.getMethodExpression()).thenReturn(referenceExpression);
        when(referenceExpression.getReferenceName()).thenReturn(methodName);

        if (loopStatement instanceof PsiForStatement) {
            when(((PsiForStatement) loopStatement).getBody()).thenReturn(loopBody);
            mockVisitor.visitForStatement((PsiForStatement) loopStatement);
        } else if (loopStatement instanceof PsiForeachStatement) {
            when(((PsiForeachStatement) loopStatement).getBody()).thenReturn(loopBody);
            mockVisitor.visitForeachStatement((PsiForeachStatement) loopStatement);
        } else if (loopStatement instanceof PsiWhileStatement) {
            when(((PsiWhileStatement) loopStatement).getBody()).thenReturn(loopBody);
            mockVisitor.visitWhileStatement((PsiWhileStatement) loopStatement);
        } else if (loopStatement instanceof PsiDoWhileStatement) {
            when(((PsiDoWhileStatement) loopStatement).getBody()).thenReturn(loopBody);
            mockVisitor.visitDoWhileStatement((PsiDoWhileStatement) loopStatement);
        }
    }

    private static Stream<TestCase> provideTestCases() {
        return Stream.of(
            new TestCase(mock(PsiForStatement.class), "com.azure.ai.textanalytics", 1, "detectLanguage", false),
            new TestCase(mock(PsiForeachStatement.class), "com.azure.ai.textanalytics", 1, "detectLanguage", false),
            new TestCase(mock(PsiWhileStatement.class), "com.azure.ai.textanalytics", 1, "detectLanguage", false),
            new TestCase(mock(PsiDoWhileStatement.class), "com.azure.ai.textanalytics", 1, "detectLanguage", false),
            new TestCase(mock(PsiForStatement.class), "com.azure.ai.textanalytics", 1, "detectLanguage", true),
            new TestCase(mock(PsiForeachStatement.class), "com.azure.ai.textanalytics", 1, "detectLanguage", true),
            new TestCase(mock(PsiWhileStatement.class), "com.azure.ai.textanalytics", 1, "detectLanguage", true),
            new TestCase(mock(PsiDoWhileStatement.class), "com.azure.ai.textanalytics", 1, "detectLanguage", true),
            new TestCase(mock(PsiForStatement.class), "com.microsoft.azure.storage.blob", 0, "detectLanguage", true),
            new TestCase(mock(PsiForStatement.class), "com.azure.ai.textanalytics", 0, "differentMethodName", true)
        );
    }

    private static class TestCase {
        PsiStatement loopStatement;
        String packageName;
        int numberOfInvocations;
        String methodName;
        boolean isDeclaration;

        TestCase(PsiStatement loopStatement, String packageName, int numberOfInvocations, String methodName,
            boolean isDeclaration) {
            this.loopStatement = loopStatement;
            this.packageName = packageName;
            this.numberOfInvocations = numberOfInvocations;
            this.methodName = methodName;
            this.isDeclaration = isDeclaration;
        }
    }
}
