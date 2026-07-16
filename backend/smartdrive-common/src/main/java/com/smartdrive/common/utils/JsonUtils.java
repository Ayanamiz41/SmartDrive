package com.smartdrive.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class JsonUtils {
    private static final Logger logger = LoggerFactory.getLogger(JsonUtils.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String convertObj2Json(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            logger.error("JSON序列化失败", e);
            return null;
        }
    }

    public static <T> T convertJson2Obj(String json, Class<T> classz) {
        try {
            return MAPPER.readValue(json, classz);
        } catch (JsonProcessingException e) {
            logger.error("JSON反序列化失败, json={}", json, e);
            return null;
        }
    }

    public static <T> List<T> convertJsonArray2List(String json, Class<T> classz) {
        try {
            return MAPPER.readValue(json, new TypeReference<List<T>>() {});
        } catch (JsonProcessingException e) {
            logger.error("JSON数组反序列化失败, json={}", json, e);
            return null;
        }
    }
}
