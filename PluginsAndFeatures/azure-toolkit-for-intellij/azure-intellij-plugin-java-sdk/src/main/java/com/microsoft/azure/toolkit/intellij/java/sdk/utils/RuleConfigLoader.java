// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.toolkit.intellij.java.sdk.models.RuleConfig;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RuleConfigLoader {
    private static final String CONFIG_FILE_PATH = "./META-INF/ruleConfigs.json";
    private static final Logger LOGGER = Logger.getLogger(RuleConfigLoader.class.getName());
    private static final RuleConfigLoader INSTANCE;

    private final Map<String, RuleConfig> ruleConfigs;

    static {
        RuleConfigLoader tempInstance;
        try {
            tempInstance = new RuleConfigLoader(CONFIG_FILE_PATH);
        } catch (IOException e) {
            tempInstance = null;
            LOGGER.log(Level.SEVERE, "Failed to initialize RuleConfigLoader: " + e.getMessage(), e);
        }
        INSTANCE = tempInstance;
    }

    private RuleConfigLoader(String filePath) throws IOException {
        this.ruleConfigs = loadRuleConfigurations(filePath);
    }

    public static RuleConfigLoader getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("RuleConfigLoader initialization failed. Check logs for details.");
        }
        return INSTANCE;
    }

    public RuleConfig getRuleConfig(String key) {
        return ruleConfigs.getOrDefault(key, RuleConfig.EMPTY_RULE);
    }

    private Map<String, RuleConfig> loadRuleConfigurations(String filePath) throws IOException {
        InputStream configStream = getClass().getClassLoader().getResourceAsStream(filePath);
        if (configStream == null) {
            throw new IOException("Configuration file not found: " + filePath);
        }

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(configStream);
        Map<String, RuleConfig> configs = new HashMap<>();

        rootNode.fields().forEachRemaining(entry -> {
            String ruleName = entry.getKey();
            JsonNode ruleNode = entry.getValue();

            if (ruleNode.path("hasDerivedRules").asBoolean(false)) {
                // Parse derived rules
                ruleNode.fields().forEachRemaining(derivedEntry -> {
                    if (!derivedEntry.getKey().equals("hasDerivedRules")) {
                        String derivedRuleName = derivedEntry.getKey();
                        JsonNode derivedRuleNode = derivedEntry.getValue();
                        RuleConfig derivedRuleConfig = parseRuleConfig(derivedRuleNode);
                        configs.put(derivedRuleName, derivedRuleConfig);
                    }
                });
            } else {
                RuleConfig ruleConfig = parseRuleConfig(ruleNode);
                configs.put(ruleName, ruleConfig);
            }
        });

        return configs;
    }

    private RuleConfig parseRuleConfig(JsonNode ruleNode) {
        List<String> usages = parseStringOrArray(ruleNode.path("usages"));
        List<String> scope = parseStringOrArray(ruleNode.path("scope"));
        String antiPatternMessage = ruleNode.path("antiPatternMessage").asText(null);
        Map<String, String> regexPatterns = parseRegexPatterns(ruleNode.path("regexPatterns"));

        return new RuleConfig(usages, scope, antiPatternMessage, regexPatterns);
    }

    //private Map<String, RuleConfig> loadRuleConfigurations(String filePath) throws IOException {
    //    InputStream configStream = getClass().getClassLoader().getResourceAsStream(filePath);
    //    if (configStream == null) {
    //        throw new IOException("Configuration file not found: " + filePath);
    //    }
    //
    //    ObjectMapper objectMapper = new ObjectMapper();
    //    JsonNode rootNode = objectMapper.readTree(configStream);
    //    Map<String, RuleConfig> configs = new HashMap<>();
    //
    //    rootNode.fields().forEachRemaining(entry -> {
    //        String ruleName = entry.getKey();
    //        JsonNode ruleNode = entry.getValue();
    //        RuleConfig ruleConfig = parseRuleConfig(ruleNode);
    //        configs.put(ruleName, ruleConfig);
    //    });
    //
    //    return configs;
    //}
    //
    //private RuleConfig parseRuleConfig(JsonNode ruleNode) {
    //    List<String> usages = parseStringOrArray(ruleNode.path("usages"));
    //    List<String> scope = parseStringOrArray(ruleNode.path("scope"));
    //    String antiPatternMessage = ruleNode.path("antiPatternMessage").asText(null);
    //    String solution = ruleNode.path("solution").asText(null);
    //
    //    Map<String, String> regexPatterns = parseRegexPatterns(ruleNode.path("regexPatterns"));
    //
    //    return new RuleConfig(usages, scope, antiPatternMessage, regexPatterns);
    //}

    private List<String> parseStringOrArray(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isTextual()) {
            values.add(node.asText());
        } else if (node.isArray()) {
            node.forEach(element -> values.add(element.asText()));
        }
        return values;
    }

    private Map<String, String> parseRegexPatterns(JsonNode regexPatternsNode) {
        Map<String, String> regexPatterns = new HashMap<>();
        if (regexPatternsNode != null && regexPatternsNode.isObject()) {
            regexPatternsNode.fields().forEachRemaining(entry -> regexPatterns.put(entry.getKey(), entry.getValue().asText()));
        }
        return regexPatterns;
    }

    //private List<Map<String, String>> parseCheckItems(JsonNode checkItemsNode) {
    //    List<Map<String, String>> result = new ArrayList<>();
    //    if (checkItemsNode.isArray()) {
    //        for (JsonNode itemNode : checkItemsNode) {
    //            if (itemNode.isObject()) {
    //                result.add(parseToMap(itemNode));
    //            } else {
    //                throw new IllegalArgumentException("Each item in 'checkItems' must be a JSON object.");
    //            }
    //        }
    //    } else {
    //        throw new IllegalArgumentException("'checkItems' must be an array of JSON objects.");
    //    }
    //    return result;
    //}

    //public Object parseCheckItems(JsonNode entryNode) {
    //    // The unified object that will contain either a List or Map depending on the input
    //    Map<String, Object> result = new HashMap<>();
    //
    //    if (entryNode.isArray()) {
    //        if (entryNode.size() > 0 && entryNode.get(0).isObject()) {
    //            // Case: List of Maps
    //            result.put("itemsAsMap", getItemsAsMap(entryNode));
    //        } else {
    //            // Case: List of Strings
    //            result.put("itemsAsList", getItemsAsList(entryNode));
    //        }
    //    } else if (entryNode.isTextual()) {
    //        // Case: Single String
    //        List<String> singleItemList = new ArrayList<>();
    //        singleItemList.add(entryNode.asText());
    //        result.put("itemsAsList", singleItemList);
    //    } else {
    //        throw new IllegalArgumentException("Invalid entry format. Must be a string, list of strings, or list of objects.");
    //    }
    //
    //    return result; // Return a unified object (Map) for flexible extraction
    //}

    // Helper to extract items as a List of Strings
    public List<String> getItemsAsList(JsonNode entryNode) {
        List<String> result = new ArrayList<>();
        if (entryNode.isArray()) {
            for (JsonNode itemNode : entryNode) {
                if (itemNode.isTextual()) {
                    result.add(itemNode.asText());
                } else {
                    throw new IllegalArgumentException("Array must contain only strings.");
                }
            }
        }
        return result;
    }

    // Helper to extract items as a List of Maps<String, String>
    public List<Map<String, String>> getItemsAsMap(JsonNode entryNode) {
        List<Map<String, String>> result = new ArrayList<>();
        if (entryNode.isArray()) {
            for (JsonNode itemNode : entryNode) {
                if (itemNode.isObject()) {
                    result.add(parseToMap(itemNode));
                } else {
                    throw new IllegalArgumentException("List must contain only objects for 'List<Map<String, String>>' format.");
                }
            }
        }
        return result;
    }

    private Object parseCheckItems(JsonNode checkItemsNode) {
        if (checkItemsNode.isArray()) {
            return parseToList(checkItemsNode);
        } else if (checkItemsNode.isObject()) {
            return parseToMap(checkItemsNode);
        }
        return checkItemsNode.asText(null);
    }

    private Map<String, String> parseToMap(JsonNode objectNode) {
        Map<String, String> map = new HashMap<>();
        objectNode.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value.isTextual()) {
                map.put(entry.getKey(), value.asText());
            } else {
                throw new IllegalArgumentException("All values in the 'checkItems' object must be strings.");
            }
        });
        return map;
    }

    private List<String> parseToList(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        arrayNode.forEach(node -> list.add(node.asText()));
        return list;
    }

    private Set<String> parseToSet(JsonNode arrayNode) {
        Set<String> set = new HashSet<>();
        arrayNode.forEach(node -> set.add(node.asText()));
        return set;
    }
}
