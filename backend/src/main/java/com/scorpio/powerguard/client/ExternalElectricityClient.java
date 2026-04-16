package com.scorpio.powerguard.client;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scorpio.powerguard.entity.Room;
import com.scorpio.powerguard.exception.BusinessException;
import com.scorpio.powerguard.model.ExternalElectricityResult;
import com.scorpio.powerguard.properties.ExternalElectricityProperties;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalElectricityClient {

    private static final String MOBILE_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 16; RMX3820 Build/BP2A.250605.015; wv) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 "
            + "Chrome/146.0.7680.120 Mobile Safari/537.36";

    private final ExternalElectricityProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ExternalElectricityResponseParser responseParser = new ExternalElectricityResponseParser(objectMapper);

    public ExternalElectricityResult queryRoomElectricity(Room room) {
        String jsonData = buildJsonData(room);
        String formBody = buildFormBody(jsonData);
        String responseBody;

        /*
         * 这里显式按学校接口的 curl 头部组装请求：
         * 1. Content-Type 必须是 application/x-www-form-urlencoded，而不是 multipart/form-data。
         * 2. account 固定取配置项，而 building/room 信息按房间动态替换。
         * 3. User-Agent 固定使用抓包验证过的移动端值，Cookie / Referer 从配置读取。
         * 4. 每个房间单独请求；某个房间失败时上层只记录日志，不影响其他房间继续拉取。
         */
        try (HttpResponse response = HttpRequest.post(properties.getUrl())
            //.header("Host", "tysf.ahpu.edu.cn:8063")
            .header("User-Agent", MOBILE_USER_AGENT)
            //.header("Accept", properties.getAccept())
            //.header("Accept-Encoding", properties.getAcceptEncoding())
            .header("Pragma", "no-cache")
            //.header("Cache-Control", "no-cache")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Content-Type", ContentType.FORM_URLENCODED.getValue() + "; charset=UTF-8")
            .header("Origin", properties.getOrigin())
            .header("Referer", properties.getReferer())
            //.header("Accept-Language", properties.getAcceptLanguage())
            .header("Cookie", properties.getCookie())
            .body(formBody)
            .timeout(properties.getTimeoutMillis())
            .execute()) {
            responseBody = response.body();
            System.out.println(responseBody);
            if (!response.isOk()) {
                throw new BusinessException(502, "学校电量接口返回非200状态");
            }
        } catch (Exception ex) {
            log.error("Failed to call electricity API for roomId={}, roomName={}, buildingId={}, buildingName={}",
                room.getRoomId(), room.getRoomName(), room.getBuildingId(), room.getBuildingName(), ex);
            throw new BusinessException(502, "调用学校电量接口失败");
        }

        return parseResponse(responseBody, room);
    }

    private String buildFormBody(String jsonData) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("jsondata", jsonData);
        form.put("funname", "synjones.onecard.query.elec.roominfo");
        form.put("json", "true");
        return form.entrySet().stream()
            .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
            .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String buildJsonData(Room room) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> queryNode = new LinkedHashMap<>();
        queryNode.put("aid", "0030000000002501");
        queryNode.put("account", properties.getFixedAccount());
        queryNode.put("room", Map.of("roomid", room.getRoomId(), "room", room.getRoomId()));
        queryNode.put("floor", Map.of("floorid", "", "floor", ""));
        queryNode.put("area", Map.of("area", properties.getArea(), "areaname", properties.getArea()));
        queryNode.put("building", Map.of("buildingid", room.getBuildingId(), "building", room.getBuildingName()));
        root.put("query_elec_roominfo", queryNode);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (IOException ex) {
            throw new BusinessException(500, "构建电量接口请求报文失败");
        }
    }

    private ExternalElectricityResult parseResponse(String responseBody, Room room) {
        try {
            return responseParser.parse(responseBody);
        } catch (BusinessException ex) {
            log.error("Failed to parse electricity API response for roomId={}, roomName={}, buildingId={}, buildingName={}, raw={}",
                room.getRoomId(), room.getRoomName(), room.getBuildingId(), room.getBuildingName(),
                StrUtil.subPre(responseBody, 500), ex);
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to parse electricity API response for roomId={}, roomName={}, buildingId={}, buildingName={}, raw={}",
                room.getRoomId(), room.getRoomName(), room.getBuildingId(), room.getBuildingName(),
                StrUtil.subPre(responseBody, 500), ex);
            throw new BusinessException(502, "解析学校电量接口响应失败");
        }
    }
}
