// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
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
 * This class is used to test the EndpointOnNonAzureOpenAIAuthCheck class.
 * The EndpointOnNonAzureOpenAIAuthCheck class is a LocalInspectionTool that checks if the endpoint method is used with KeyCredential for non-Azure OpenAI clients.
 * If the endpoint method is used with KeyCredential for non-Azure OpenAI clients, a warning is registered.
 * An example that should be flagged is:
 * OpenAI Client client = new OpenAIClientBuilder()
 *     .credential(new KeyCredential("key"))
 *     .endpoint("endpoint")
 *     .buildClient();
 */
public class EndpointOnNonAzureOpenAIAuthCheckTest {

    @Mock
    private ProblemsHolder mockHolder;

    @Mock
    private PsiMethodCallExpression mockMethodCall;

    @BeforeEach
    public void setup() {
        mockHolder = mock(ProblemsHolder.class);
        mockMethodCall = mock(PsiMethodCallExpression.class);
    }

    @ParameterizedTest
    @MethodSource("testCases")
    public void testEndpointOnNonAzureOpenAIClient(TestCase testCase) {

        setupMockMethodExpression(testCase.numOfInvocation, testCase.endpoint, testCase.credential,
            testCase.keyCredentialPackageName, testCase.azurePackageName);
        EndpointOnNonAzureOpenAIAuthCheck.EndpointOnNonAzureOpenAIAuthVisitor visitor = new EndpointOnNonAzureOpenAIAuthCheck.EndpointOnNonAzureOpenAIAuthVisitor(mockHolder);
        visitor.visitMethodCallExpression(mockMethodCall);

        verify(mockHolder, times(testCase.numOfInvocation)).registerProblem(mockMethodCall, "Endpoint API should not " +
            "be used with KeyCredential for non-Azure OpenAI clients.");
    }

    private static Stream<TestCase> testCases() {
        return Stream.of(
            new TestCase(1, "endpoint", "credential", "KeyCredential", "com.azure.ai.openai"),
            new TestCase(0, "notEndpoint", "credential", "KeyCredential", "com.azure.ai.openai"),
            new TestCase(0, "endpoint", "notCredential", "KeyCredential", "com.azure.ai.openai"),
            new TestCase(0, "endpoint", "com.azure.core.credential.AzureKeyCredential", "com.azure.ai.openai", "com.azure.ai.openai")
        );
    }

    private void setupMockMethodExpression(int numOfInvocation, String endpoint, String credential, String keyCredentialPackageName, String azurePackageName) {
        PsiReferenceExpression methodExpression = mock(PsiReferenceExpression.class);
        PsiMethodCallExpression qualifierOne = mock(PsiMethodCallExpression.class);
        PsiReferenceExpression methodExpressionOne = mock(PsiReferenceExpression.class);
        PsiNewExpression newExpression = mock(PsiNewExpression.class);
        PsiExpressionList argumentList = mock(PsiExpressionList.class);
        PsiExpression[] arguments = new PsiExpression[]{newExpression};
        PsiJavaCodeReferenceElement classReference = mock(PsiJavaCodeReferenceElement.class);
        PsiVariable parent = mock(PsiVariable.class);
        PsiType qualifierType = mock(PsiType.class);

        when(mockMethodCall.getMethodExpression()).thenReturn(methodExpression);
        when(methodExpression.getReferenceName()).thenReturn(endpoint);
        when(methodExpression.getQualifierExpression()).thenReturn(qualifierOne);
        when(qualifierOne.getMethodExpression()).thenReturn(methodExpressionOne);
        when(methodExpressionOne.getReferenceName()).thenReturn(credential);
        when(qualifierOne.getArgumentList()).thenReturn(argumentList);
        when(argumentList.getExpressions()).thenReturn(arguments);
        when(newExpression.getClassReference()).thenReturn(classReference);
        when(classReference.getReferenceName()).thenReturn(keyCredentialPackageName);
        when(qualifierOne.getParent()).thenReturn(parent);
        when(parent.getType()).thenReturn(qualifierType);
        when(qualifierType.getCanonicalText()).thenReturn(azurePackageName);

    }

    private static class TestCase {
        int numOfInvocation;
        String endpoint;
        String credential;
        String keyCredentialPackageName;
        String azurePackageName;

        TestCase(int numOfInvocation, String endpoint, String credential, String keyCredentialPackageName, String azurePackageName) {
            this.numOfInvocation = numOfInvocation;
            this.endpoint = endpoint;
            this.credential = credential;
            this.keyCredentialPackageName = keyCredentialPackageName;
            this.azurePackageName = azurePackageName;
        }
    }
}
