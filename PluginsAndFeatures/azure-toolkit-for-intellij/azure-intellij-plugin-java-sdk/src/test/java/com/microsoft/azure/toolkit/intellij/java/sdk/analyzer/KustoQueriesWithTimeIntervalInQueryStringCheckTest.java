// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.microsoft.azure.toolkit.intellij.java.sdk.analyzer.KustoQueriesWithTimeIntervalInQueryStringCheck.KustoQueriesVisitor;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * This class tests the KustoQueriesWithTimeIntervalInQueryStringCheck class.
 * <p>
 * These are some example queries that should be flagged:
 * <ul>
 * <li> "String query1 = \"| where timestamp > ago(1d)\";": This query uses the ago function to create a time interval of 1 day. This should be FLAGGED </li>
 * <li> "String query2 = \"| where timestamp > datetime(2021-01-01)\";": This query compares the timestamp to a specific datetime. This should be FLAGGED </li>
 * <li> "String query3 = \"| where timestamp > now()\";": This query uses the now function to get the current timestamp. This should be FLAGGED </li>
 * <li> "String query4 = \"| where timestamp > startofday()\";": This query uses the startofday function to get the start of the current day. This should be FLAGGED </li>
 * <li> "String query5 = \"| where timestamp > startofmonth()\";": This query uses the startofmonth function to get the start of the current month. This should be FLAGGED </li>
 * <li> "String query11 = \"| where timestamp > ago(" + days + ")\";": This query uses a variable to define the time interval. This should be FLAGGED </li>
 * <li> "String query12 = \"| where timestamp > datetime(" + date + ")\";": This query uses a variable to define the datetime. This should not be FLAGGED </li>
 * </ul>
 */
public class KustoQueriesWithTimeIntervalInQueryStringCheckTest {

    @Mock
    private ProblemsHolder mockHolder;
    @Mock
    private PsiElementVisitor mockVisitor;
    @Mock
    private PsiMethodCallExpressionImpl methodCall;
    @Mock
    private PsiLocalVariable mockVariable;

    @Mock
    private PsiPolyadicExpression mockPolyadicExpression;

    @BeforeEach
    public void setup() {
        mockHolder = mock(ProblemsHolder.class);
        mockVisitor = new KustoQueriesWithTimeIntervalInQueryStringCheck.KustoQueriesVisitor(mockHolder);
        mockVariable = mock(PsiLocalVariable.class);
        mockPolyadicExpression = mock(PsiPolyadicExpression.class);
    }

    @ParameterizedTest
    @MethodSource("testCases")
    public void testKustoQueriesWithTimeIntervalInQueryStringCheck(TestCase testCase) {
        setupWithLocalVariable(testCase.queryString, testCase.packageName, testCase.numOfInvocations);
        // Visit the variable to store its name if it's a query string
        mockVisitor.visitElement(mockVariable);

        // Visit the method call to check if the query variable is used and the method call is to an Azure client
        mockVisitor.visitElement(methodCall);

        // Verify that the problem was registered correctly for the method call
        verify(mockHolder, times(testCase.numOfInvocations)).registerProblem(eq(methodCall), contains("KQL queries " +
            "with time intervals in the query string detected."));

    }

    @ParameterizedTest
    @MethodSource("testCases")
    public void testKustoQueriesWithTimeIntervalInQueryWithPolyadicExpression(TestCase testCase) {
        setupWithPolyadicExpression(testCase.queryString, testCase.packageName, testCase.numOfInvocations);
        // Visit the variable to store its name if it's a query string
        mockVisitor.visitElement(mockPolyadicExpression);

        // Visit the method call to check if the query variable is used and the method call is to an Azure client
        mockVisitor.visitElement(methodCall);

        // Verify that the problem was registered correctly for the method call
        verify(mockHolder, times(testCase.numOfInvocations)).registerProblem(eq(methodCall), contains("KQL queries " +
            "with " +
            "time intervals in the query string detected."));

    }

    @Test
    public void testCheckExpressionWithNullExpression() {
        PsiExpression nullExpression = null;
        PsiLocalVariable mockElement = mock(PsiLocalVariable.class);
        ((KustoQueriesVisitor) mockVisitor).checkExpressionForPatterns(nullExpression, mockElement);
    }

    private static Stream<TestCase> testCases() {
        return Stream.of(
            new TestCase("datetime(startDate)", "com.azure", 1),
            new TestCase("filter.datetime(2022-01-01)", "com.azure", 1),
            new TestCase("time.now()", "com.azure", 1),
            new TestCase("time.startofday()", "com.azure", 1),
            new TestCase("time.startofmonth()", "com.azure", 1),
            new TestCase("range.between(datetime(2022-01-01), datetime(2022-02-01)", "com.azure", 1),
            new TestCase("datetime(startDate)", "com.microsoft.azure", 0)
        );
    }

    /**
     * This method tests the registerProblem method with a local variable as the query string
     */
    private void setupWithLocalVariable(String queryString, String packageName, int numOfInvocations) {

        mockVariable = mock(PsiLocalVariable.class);
        PsiLiteralExpression initializer = mock(PsiLiteralExpression.class);
        PsiLocalVariable parentElement = mock(PsiLocalVariable.class);
        PsiClass containingClass = mock(PsiClass.class);

        methodCall = mock(PsiMethodCallExpressionImpl.class);
        PsiExpressionList argumentList = mock(PsiExpressionList.class);
        PsiReferenceExpression argument = mock(PsiReferenceExpression.class);
        PsiExpression[] arguments = new PsiExpression[]{argument};
        PsiReferenceExpression referenceExpression = mock(PsiReferenceExpression.class);
        PsiVariable resolvedElement = mock(PsiVariable.class);
        PsiReferenceExpression qualifierExpression = mock(PsiReferenceExpression.class);

        // stubs for handle local variable method
        when(mockVariable.getInitializer()).thenReturn(initializer);
        when(mockVariable.getName()).thenReturn("stringQuery");

        // stubs for checkExpression method
        when(initializer.getText()).thenReturn(queryString);
        when(mockVariable.getParent()).thenReturn(parentElement);
        when(parentElement.getName()).thenReturn("stringQuery");

        // stubs for handleMethodCall method
        when(methodCall.getArgumentList()).thenReturn(argumentList);
        when(argumentList.getExpressions()).thenReturn(arguments);
        when(argument.resolve()).thenReturn(resolvedElement);
        when(resolvedElement.getName()).thenReturn("stringQuery");

        // stubs for isAzureClient method
        when(methodCall.getMethodExpression()).thenReturn(referenceExpression);
        when(referenceExpression.getQualifierExpression()).thenReturn(qualifierExpression);
        when(PsiTreeUtil.getParentOfType(methodCall, PsiClass.class)).thenReturn(containingClass);
        when(containingClass.getQualifiedName()).thenReturn(packageName);

   }

    void setupWithPolyadicExpression(String queryString, String packageName, int numOfInvocations) {

        mockPolyadicExpression = mock(PsiPolyadicExpression.class);
        PsiExpression initializer = mock(PsiExpression.class);
        PsiLocalVariable parentElement = mock(PsiLocalVariable.class);
        PsiClass containingClass = mock(PsiClass.class);

        // stubs for handlePolyadicExpression method
        when(mockPolyadicExpression.getText()).thenReturn(queryString);

        // stubs for checkExpression method
        when(initializer.getText()).thenReturn(queryString);
        when(mockPolyadicExpression.getParent()).thenReturn(parentElement);
        when(parentElement.getName()).thenReturn("stringQuery");

        methodCall = mock(PsiMethodCallExpressionImpl.class);
        PsiExpressionList argumentList = mock(PsiExpressionList.class);
        PsiReferenceExpression argument = mock(PsiReferenceExpression.class);
        PsiExpression[] arguments = new PsiExpression[]{argument};
        PsiReferenceExpression referenceExpression = mock(PsiReferenceExpression.class);
        PsiVariable resolvedElement = mock(PsiVariable.class);
        PsiReferenceExpression qualifierExpression = mock(PsiReferenceExpression.class);

        // stubs for handleMethodCall method
        when(methodCall.getArgumentList()).thenReturn(argumentList);
        when(argumentList.getExpressions()).thenReturn(arguments);
        when(argument.resolve()).thenReturn(resolvedElement);
        when(resolvedElement.getName()).thenReturn("stringQuery");

        // stubs for isAzureClient method
        when(methodCall.getMethodExpression()).thenReturn(referenceExpression);
        when(referenceExpression.getQualifierExpression()).thenReturn(qualifierExpression);
        when(PsiTreeUtil.getParentOfType(methodCall, PsiClass.class)).thenReturn(containingClass);
        when(containingClass.getQualifiedName()).thenReturn(packageName);

         }

    private static class TestCase {
        String queryString;
        String packageName;
        int numOfInvocations;

        TestCase(String queryString, String packageName, int numOfInvocations) {
            this.queryString = queryString;
            this.packageName = packageName;
            this.numOfInvocations = numOfInvocations;
        }
    }
}
