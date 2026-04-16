package com.scorpio.powerguard.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scorpio.powerguard.exception.BusinessException;
import com.scorpio.powerguard.model.ExternalElectricityResult;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExternalElectricityResponseParserTest {

    private ExternalElectricityResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new ExternalElectricityResponseParser(new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void shouldParseRemainFromErrmsg() {
        String payload = """
            {
              "query_elec_roominfo": {
                "retcode": "0",
                "errmsg": "房间当当前剩余电量46.19",
                "aid": "0030000000002501",
                "account": "54963",
                "meterflag": "amt",
                "bal": "",
                "price": "0",
                "pkgflag": "none",
                "area": {
                  "area": "安徽工程大学",
                  "areaname": "安徽工程大学"
                },
                "building": {
                  "buildingid": "10",
                  "building": "男19#楼"
                },
                "floor": {
                  "floorid": "",
                  "floor": ""
                },
                "room": {
                  "roomid": "215",
                  "room": "215"
                },
                "pkgtab": []
              }
            }
            """;

        ExternalElectricityResult result = parser.parse(payload);

        assertEquals(new BigDecimal("46.19"), result.getRemain());
        assertNull(result.getTotal());
        assertEquals("房间当当前剩余电量46.19", result.getMessage());
        assertEquals("54963", result.getAccount());
        assertEquals("215", result.getRoomName());
        assertEquals("男19#楼", result.getBuildingName());
        assertEquals(payload, result.getRawResponse());
    }

    @Test
    void shouldFallbackToFirstFloatInErrmsg() {
        String payload = """
            {
              "query_elec_roominfo": {
                "retcode": "0",
                "errmsg": "当前可用电量为 12.34 度",
                "account": "54963",
                "building": {
                  "building": "男19#楼"
                },
                "room": {
                  "room": "215"
                }
              }
            }
            """;

        ExternalElectricityResult result = parser.parse(payload);

        assertEquals(new BigDecimal("12.34"), result.getRemain());
    }

    @Test
    void shouldRejectMissingQueryElecRoomInfo() {
        BusinessException ex = assertThrows(BusinessException.class, () -> parser.parse("{\"foo\":{}}"));

        assertEquals(502, ex.getCode());
        assertTrue(ex.getMessage().contains("query_elec_roominfo"));
    }

    @Test
    void shouldRejectNonObjectQueryElecRoomInfo() {
        String payload = """
            {
              "query_elec_roominfo": "invalid"
            }
            """;

        BusinessException ex = assertThrows(BusinessException.class, () -> parser.parse(payload));

        assertEquals(502, ex.getCode());
        assertTrue(ex.getMessage().contains("query_elec_roominfo"));
    }

    @Test
    void shouldRejectBlankErrmsg() {
        String payload = """
            {
              "query_elec_roominfo": {
                "retcode": "0",
                "errmsg": "   ",
                "account": "54963",
                "building": {
                  "building": "男19#楼"
                },
                "room": {
                  "room": "215"
                }
              }
            }
            """;

        BusinessException ex = assertThrows(BusinessException.class, () -> parser.parse(payload));

        assertEquals(502, ex.getCode());
        assertTrue(ex.getMessage().contains("errmsg"));
    }

    @Test
    void shouldRejectNonZeroRetcode() {
        String payload = """
            {
              "query_elec_roominfo": {
                "retcode": "1",
                "errmsg": "房间不存在",
                "account": "54963",
                "building": {
                  "building": "男19#楼"
                },
                "room": {
                  "room": "215"
                }
              }
            }
            """;

        BusinessException ex = assertThrows(BusinessException.class, () -> parser.parse(payload));

        assertEquals(502, ex.getCode());
        assertTrue(ex.getMessage().contains("retcode=1"));
        assertTrue(ex.getMessage().contains("房间不存在"));
    }

    @Test
    void shouldRejectMissingAccount() {
        String payload = """
            {
              "query_elec_roominfo": {
                "retcode": "0",
                "errmsg": "房间当当前剩余电量46.19",
                "building": {
                  "building": "男19#楼"
                },
                "room": {
                  "room": "215"
                }
              }
            }
            """;

        BusinessException ex = assertThrows(BusinessException.class, () -> parser.parse(payload));

        assertEquals(502, ex.getCode());
        assertTrue(ex.getMessage().contains("account"));
    }

    @Test
    void shouldRejectMissingRoomName() {
        String payload = """
            {
              "query_elec_roominfo": {
                "retcode": "0",
                "errmsg": "房间当当前剩余电量46.19",
                "account": "54963",
                "building": {
                  "building": "男19#楼"
                },
                "room": {
                  "room": " "
                }
              }
            }
            """;

        BusinessException ex = assertThrows(BusinessException.class, () -> parser.parse(payload));

        assertEquals(502, ex.getCode());
        assertTrue(ex.getMessage().contains("房间名称"));
    }

    @Test
    void shouldRejectMissingBuildingName() {
        String payload = """
            {
              "query_elec_roominfo": {
                "retcode": "0",
                "errmsg": "房间当当前剩余电量46.19",
                "account": "54963",
                "building": {
                  "building": " "
                },
                "room": {
                  "room": "215"
                }
              }
            }
            """;

        BusinessException ex = assertThrows(BusinessException.class, () -> parser.parse(payload));

        assertEquals(502, ex.getCode());
        assertTrue(ex.getMessage().contains("楼栋名称"));
    }

    @Test
    void shouldRejectErrmsgWithoutNumber() {
        String payload = """
            {
              "query_elec_roominfo": {
                "retcode": "0",
                "errmsg": "查询成功",
                "account": "54963",
                "building": {
                  "building": "男19#楼"
                },
                "room": {
                  "room": "215"
                }
              }
            }
            """;

        BusinessException ex = assertThrows(BusinessException.class, () -> parser.parse(payload));

        assertEquals(502, ex.getCode());
        assertTrue(ex.getMessage().contains("errmsg"));
    }
}
