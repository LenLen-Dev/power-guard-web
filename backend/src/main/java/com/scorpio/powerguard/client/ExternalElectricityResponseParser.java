package com.scorpio.powerguard.client;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scorpio.powerguard.exception.BusinessException;
import com.scorpio.powerguard.model.ExternalElectricityResult;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ExternalElectricityResponseParser {

    private static final Pattern PRIMARY_BALANCE_PATTERN =
        Pattern.compile("剩余电量\\s*([+-]?\\d+(?:\\.\\d+)?)");
    private static final Pattern FLOAT_PATTERN =
        Pattern.compile("([+-]?\\d+(?:\\.\\d+)?)");
    private static final List<String> TOTAL_KEYS = List.of(
        "total", "totalvalue", "totalelec", "allelec", "limitelec", "maxelec"
    );

    private final ObjectMapper objectMapper;

    ExternalElectricityResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ExternalElectricityResult parse(String responseBody) {
        JsonNode rootNode = readRootNode(responseBody);
        JsonNode roomInfo = requireObject(rootNode, "query_elec_roominfo", "学校电量接口响应缺少 query_elec_roominfo 对象");

        String errmsg = requireNonBlankText(roomInfo.get("errmsg"), "学校电量接口响应缺少 errmsg 字段");
        String retcode = safeText(roomInfo.get("retcode"));
        if (!"0".equals(retcode)) {
            throw new BusinessException(502, "学校电量接口返回失败: retcode=" + retcode + ", errmsg=" + errmsg);
        }

        String account = requireNonBlankText(roomInfo.get("account"), "学校电量接口响应缺少 account 字段");
        String roomName = requireNestedNonBlankText(roomInfo, "room", "room", "学校电量接口响应缺少房间名称字段");
        String buildingName = requireNestedNonBlankText(roomInfo, "building", "building", "学校电量接口响应缺少楼栋名称字段");
        BigDecimal remain = extractRemain(errmsg);
        BigDecimal total = extractDecimal(rootNode, TOTAL_KEYS);

        return new ExternalElectricityResult(total, remain, responseBody, errmsg, account, roomName, buildingName);
    }

    private JsonNode readRootNode(String responseBody) {
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            if (rootNode == null || !rootNode.isObject()) {
                throw new BusinessException(502, "学校电量接口响应不是有效JSON对象");
            }
            return rootNode;
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException(502, "学校电量接口响应不是有效JSON对象");
        }
    }

    private JsonNode requireObject(JsonNode parent, String fieldName, String errorMessage) {
        JsonNode child = parent == null ? null : parent.get(fieldName);
        if (child == null || !child.isObject()) {
            throw new BusinessException(502, errorMessage);
        }
        return child;
    }

    private String requireNestedNonBlankText(JsonNode parent, String objectField, String valueField, String errorMessage) {
        JsonNode objectNode = parent == null ? null : parent.get(objectField);
        if (objectNode == null || !objectNode.isObject()) {
            throw new BusinessException(502, errorMessage);
        }
        return requireNonBlankText(objectNode.get(valueField), errorMessage);
    }

    private String requireNonBlankText(JsonNode node, String errorMessage) {
        String text = safeText(node);
        if (StrUtil.isBlank(text)) {
            throw new BusinessException(502, errorMessage);
        }
        return text;
    }

    private BigDecimal extractRemain(String errmsg) {
        Matcher primary = PRIMARY_BALANCE_PATTERN.matcher(errmsg);
        if (primary.find()) {
            return parseBigDecimal(primary.group(1), "无法从学校电量接口 errmsg 中解析剩余电量");
        }

        Matcher fallback = FLOAT_PATTERN.matcher(errmsg);
        if (fallback.find()) {
            return parseBigDecimal(fallback.group(1), "无法从学校电量接口 errmsg 中解析剩余电量");
        }

        throw new BusinessException(502, "无法从学校电量接口 errmsg 中解析剩余电量");
    }

    private BigDecimal parseBigDecimal(String value, String errorMessage) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            throw new BusinessException(502, errorMessage);
        }
    }

    private BigDecimal extractDecimal(JsonNode node, List<String> candidateKeys) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (matches(entry.getKey(), candidateKeys)) {
                    BigDecimal value = parseDecimal(entry.getValue());
                    if (value != null) {
                        return value;
                    }
                }
                BigDecimal nestedValue = extractDecimal(entry.getValue(), candidateKeys);
                if (nestedValue != null) {
                    return nestedValue;
                }
            }
            return null;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                BigDecimal nestedValue = extractDecimal(child, candidateKeys);
                if (nestedValue != null) {
                    return nestedValue;
                }
            }
        }
        return null;
    }

    private boolean matches(String fieldName, List<String> candidateKeys) {
        String normalized = fieldName == null ? "" : fieldName.replace("_", "").toLowerCase(Locale.ROOT);
        return candidateKeys.stream().anyMatch(key -> key.equalsIgnoreCase(normalized));
    }

    private BigDecimal parseDecimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            String text = node.asText();
            if (StrUtil.isBlank(text)) {
                return null;
            }
            String normalized = text.replaceAll("[^0-9.\\-]", "");
            if (StrUtil.isBlank(normalized)) {
                return null;
            }
            try {
                return new BigDecimal(normalized);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private String safeText(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return StrUtil.trim(node.asText());
    }
}
