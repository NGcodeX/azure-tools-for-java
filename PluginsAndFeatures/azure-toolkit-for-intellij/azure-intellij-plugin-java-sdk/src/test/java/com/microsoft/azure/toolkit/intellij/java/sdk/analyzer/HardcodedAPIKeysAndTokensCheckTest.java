// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiNewExpression;
import com.microsoft.azure.toolkit.intellij.java.sdk.models.RuleConfig;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test the HardcodedAPIKeysAndTokensCheck class for hardcoded API keys and tokens.
 * When a client is authenticated with AzurekeyCredentials and AccessToken, a problem is registered.
 * These are some instances that a flag would be raised.
 * 1. TextAnalyticsClient client = new TextAnalyticsClientBuilder()
 * .endpoint(endpoint)
 * .credential(new AzureKeyCredential(apiKey))
 * .buildClient();
 * <p>
 * 2. TokenCredential credential = request -> {
 * AccessToken token = new AccessToken("<your-hardcoded-token>", OffsetDateTime.now().plusHours(1));
 * }
 */
public class HardcodedAPIKeysAndTokensCheckTest {

    @Mock
    private ProblemsHolder mockHolder;

    @Mock
    private PsiElementVisitor mockVisitor;

    @BeforeEach
    public void setup() {
        mockHolder = mock(ProblemsHolder.class);
        mockVisitor = new HardcodedAPIKeysAndTokensCheck.APIKeysAndTokensVisitor(mockHolder);
    }

    @ParameterizedTest
    @MethodSource("testCases")
    public void testHardcodedAPIKeysAndTokensCheck(TestCase testCase) {
        PsiNewExpression mockExpression = mockMethodExpression(testCase.apiName, testCase.numOfInvocations);
        mockVisitor.visitElement(mockExpression);

        verify(mockHolder, times(testCase.numOfInvocations))
            .registerProblem(eq(mockExpression),
                Mockito.contains(
                    "DefaultAzureCredential is recommended for authentication if the service client supports Token Credential (Entra ID Authentication). If not, then use Azure Key Credential for API key based authentication."));
    }

    @Test
    public void testNoFlagIfNoHardcodedAPIKeysAndTokens() {
        PsiNewExpression newExpression = mock(PsiNewExpression.class);
        PsiJavaCodeReferenceElement javaCodeReferenceElement = mock(PsiJavaCodeReferenceElement.class);
        PsiLiteralExpression literalExpression = mock(PsiLiteralExpression.class);

        when(newExpression.getClassReference()).thenReturn(javaCodeReferenceElement);
        when(javaCodeReferenceElement.getReferenceName()).thenReturn("AzureKeyCredential");
        when(javaCodeReferenceElement.getQualifiedName()).thenReturn(RuleConfig.AZURE_PACKAGE_NAME);
        when(newExpression.getChildren()).thenReturn(new PsiElement[]{literalExpression});
        when(literalExpression.getValue()).thenReturn(System.getenv());

        mockVisitor.visitElement(newExpression);

        verify(mockHolder, times(0)).registerProblem(eq(newExpression),
            Mockito.contains(
                "DefaultAzureCredential is recommended for authentication if the service client supports Token Credential (Entra ID Authentication). If not, then use Azure Key Credential for API key based authentication."));
    }

    private static Stream<TestCase> testCases() {
        return Stream.of(
            new TestCase("AzureKeyCredential", 1),
            new TestCase("AccessToken", 1),
            new TestCase("KeyCredential", 1),
            new TestCase("AzureNamedKeyCredential", 1),
            new TestCase("AzureSasCredential", 1),
            new TestCase("AzureNamedKey", 1),
            new TestCase("ClientSecretCredentialBuilder", 1),
            new TestCase("UsernamePasswordCredentialBuilder", 1),
            new TestCase("BasicAuthenticationCredential", 1),
            new TestCase("SomeOtherClient", 0),
            new TestCase("", 0)
        );
    }

    private PsiNewExpression mockMethodExpression(String authServiceToCheck, int numOfInvocations) {
        PsiNewExpression newExpression = mock(PsiNewExpression.class);
        PsiJavaCodeReferenceElement javaCodeReferenceElement = mock(PsiJavaCodeReferenceElement.class);
        PsiLiteralExpression literalExpression = mock(PsiLiteralExpression.class);

        when(newExpression.getClassReference()).thenReturn(javaCodeReferenceElement);
        when(javaCodeReferenceElement.getReferenceName()).thenReturn(authServiceToCheck);
        when(javaCodeReferenceElement.getQualifiedName()).thenReturn(RuleConfig.AZURE_PACKAGE_NAME);
        when(newExpression.getChildren()).thenReturn(new PsiElement[]{literalExpression});
        when(literalExpression.getValue()).thenReturn("hardcoded-api-token");

        return newExpression;
    }

    static class TestCase {
        String apiName;
        int numOfInvocations;

        TestCase(String apiName, int numOfInvocations) {
            this.apiName = apiName;
            this.numOfInvocations = numOfInvocations;
        }
    }
}
