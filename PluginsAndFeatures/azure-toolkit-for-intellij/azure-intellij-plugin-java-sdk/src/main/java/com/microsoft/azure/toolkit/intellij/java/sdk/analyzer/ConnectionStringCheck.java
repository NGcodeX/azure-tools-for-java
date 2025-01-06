// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.microsoft.azure.toolkit.intellij.java.sdk.models.RuleConfig;
import com.microsoft.azure.toolkit.intellij.java.sdk.utils.RuleConfigLoader;

/**
 * Inspection tool to check discouraged Connection String usage.
 */
public class ConnectionStringCheck extends DetectDiscouragedAPIUsageCheck {

    @Override
    protected RuleConfig getRuleConfig() {
        return RuleConfigLoader.getInstance().getRuleConfig("ConnectionStringCheck");
    }
}