// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.microsoft.azure.toolkit.intellij.java.sdk.models.RuleConfig;
import com.microsoft.azure.toolkit.intellij.java.sdk.utils.RuleConfigLoader;

/**
 * This class extends the DetectDiscouragedClientCheck to check for the use of ServiceBusReceiverAsyncClient in the code and
 * suggests using ServiceBusProcessorClient instead. The client data is loaded from the configuration file and the
 * client name is checked against the discouraged client name. If the client name matches, a problem is registered with
 * the suggestion message.
 */
public class ServiceBusReceiverAsyncClientCheck extends DetectDiscouragedClientCheck {

    @Override
    protected RuleConfig getRuleConfig() {
        return RuleConfigLoader.getInstance().getRuleConfig("ConnectionStringCheck");
    }
}
