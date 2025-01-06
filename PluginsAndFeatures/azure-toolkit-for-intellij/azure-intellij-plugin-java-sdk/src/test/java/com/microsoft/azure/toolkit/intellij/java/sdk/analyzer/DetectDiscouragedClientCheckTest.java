// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.microsoft.azure.toolkit.intellij.java.sdk.analyzer.DetectDiscouragedClientCheck.DetectDiscouragedClientVisitor;
import com.microsoft.azure.toolkit.intellij.java.sdk.models.RuleConfig;
import java.util.Collections;
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

/**
 * This class tests the ServiceBusReceiverAsyncClientCheck class by mocking the ProblemsHolder and PsiElementVisitor
 * and verifying that a problem is registered when the ServiceBusReceiverAsyncClient is used.
 * The test also verifies that a problem is not registered when the PsiElement is null.
 *
 * Here are some examples of test data where registerProblem should be called:
 * 1. ServiceBusReceiverAsyncClient client = new ServiceBusReceiverAsyncClient();
 * 2. private ServiceBusReceiverAsyncClient receiver;
 * 3. final ServiceBusReceiverAsyncClient autoCompleteReceiver =
 *    toClose(getReceiverBuilder(false, entityType, index, false)
 *    .buildAsyncClient());
 *
 * 4. final EventHubConsumerAsyncClient consumerClient = partitionPump.getClient();
 * 5. EventHubConsumerAsyncClient eventHubConsumer = eventHubClientBuilder.buildAsyncClient()
 *    .createConsumer(claimedOwnership.getConsumerGroup(), prefetch, true);
 */
public class DetectDiscouragedClientCheckTest {

    @Mock
    private ProblemsHolder mockHolder;

    @Mock
    private JavaElementVisitor mockVisitor;

    @Mock
    private PsiTypeElement mockTypeElement;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mockHolder = mock(ProblemsHolder.class);
        mockTypeElement = mock(PsiTypeElement.class);
    }

    @ParameterizedTest
    @MethodSource("provideTestCases")
    public void detectsDiscouragedClientUsage(TestCase testCase) {
        mockVisitor = createVisitor(testCase.ruleConfig);
        setupMockElement(mockTypeElement, testCase.numOfInvocations, testCase.usagesToCheck,
            testCase.suggestionMessage);
        mockVisitor.visitTypeElement(mockTypeElement);
        verify(mockHolder, times(testCase.numOfInvocations)).registerProblem(eq(mockTypeElement), contains(testCase.suggestionMessage));
    }

    private JavaElementVisitor createVisitor(RuleConfig ruleConfig) {
        return new DetectDiscouragedClientVisitor(mockHolder, ruleConfig);
    }

    private void setupMockElement(PsiTypeElement typeElement, int numberOfInvocations, String clientToCheck, String suggestionMessage) {
        PsiType mockType = mock(PsiType.class);

        when(mockTypeElement.getType()).thenReturn(mockType);
        when(mockType.getPresentableText()).thenReturn(clientToCheck);
    }

    private static Stream<TestCase> provideTestCases() {
        RuleConfig serviceBusReceiverAsyncClientConfig = mock(RuleConfig.class);
        when(serviceBusReceiverAsyncClientConfig.getUsagesToCheck()).thenReturn(Collections.singletonList("ServiceBusReceiverAsyncClient"));
        when(serviceBusReceiverAsyncClientConfig.getAntiPatternMessage()).thenReturn("Use of ServiceBusReceiverAsyncClient detected. Use ServiceBusProcessorClient instead.");

        RuleConfig eventHubConsumerAsyncClientConfig = mock(RuleConfig.class);
        when(eventHubConsumerAsyncClientConfig.getUsagesToCheck()).thenReturn(Collections.singletonList("EventHubConsumerAsyncClient"));
        when(eventHubConsumerAsyncClientConfig.getAntiPatternMessage()).thenReturn("Use of EventHubConsumerAsyncClient detected. Use EventProcessorClient instead which provides a higher-level abstraction that simplifies event processing, making it the preferred choice for most developers.");

        return Stream.of(
            new TestCase("ServiceBusReceiverAsyncClient", "Use of ServiceBusReceiverAsyncClient detected. Use ServiceBusProcessorClient instead.", 1, serviceBusReceiverAsyncClientConfig),
            new TestCase("EventHubConsumerAsyncClient", "Use of EventHubConsumerAsyncClient detected. Use EventProcessorClient instead which provides a higher-level abstraction that simplifies event processing, making it the preferred choice for most developers.", 1, eventHubConsumerAsyncClientConfig),
            new TestCase("ServiceBusProcessorClient", "", 0, serviceBusReceiverAsyncClientConfig),
            new TestCase("", "", 0, serviceBusReceiverAsyncClientConfig)
        );
    }

    private static class TestCase {
        String usagesToCheck;
        String suggestionMessage;
        int numOfInvocations;
        RuleConfig ruleConfig;

        TestCase(String usagesToCheck, String suggestionMessage, int numOfInvocations, RuleConfig ruleConfig) {
            this.usagesToCheck = usagesToCheck;
            this.suggestionMessage = suggestionMessage;
            this.numOfInvocations = numOfInvocations;
            this.ruleConfig = ruleConfig;
        }
    }
}
