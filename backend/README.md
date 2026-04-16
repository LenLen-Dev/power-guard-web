# 高校宿舍电量监控与预警系统后端

当前后端已恢复为标准 Java Maven 项目，运行入口和源码都只保留 Spring Boot 版本。

## 目录结构

```text
backend
├─ pom.xml
├─ README.md
└─ src
   ├─ main
   │  ├─ java/com/scorpio/powerguard
   │  │  ├─ client
   │  │  ├─ common
   │  │  ├─ config
   │  │  ├─ constant
   │  │  ├─ controller
   │  │  ├─ dto
   │  │  ├─ entity
   │  │  ├─ enums
   │  │  ├─ exception
   │  │  ├─ mapper
   │  │  ├─ model
   │  │  ├─ properties
   │  │  ├─ schedule
   │  │  ├─ service
   │  │  ├─ util
   │  │  └─ vo
   │  └─ resources
   │     ├─ application.yml
   │     ├─ application-dev.yml
   │     ├─ mapper
   │     └─ sql
   └─ test
      └─ java/com/scorpio/powerguard
```

## 运行

1. 使用 JDK 17。
2. 按实际环境修改 `src/main/resources/application-dev.yml`。
3. 启动服务：

```bash
mvn spring-boot:run
```

默认端口为 `8080`。

## 测试

运行单元测试：

```bash
mvn test
```

## API

对外 API 保持不变：

- `POST /api/rooms`
- `PUT /api/rooms/{id}`
- `DELETE /api/rooms/{id}`
- `POST /api/rooms/refresh`
- `GET /api/rooms/status`
- `GET /api/rooms/{id}/trend?days=7`
