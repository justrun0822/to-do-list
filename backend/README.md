# Todo Backend (Spring Boot + Redis + MySQL + MyBatis-Plus)

## 1. 技术栈
- Spring Boot 3
- MyBatis-Plus
- MySQL 8
- Redis

## 2. 启动前准备
1. 新部署：执行 `sql/schema.sql`
2. 旧库升级：先执行 `sql/migration_v2.sql`，再执行 `sql/migration_v3.sql`
3. 确保 MySQL、Redis 已启动
4. 修改 `src/main/resources/application.yml` 数据库账号密码

## 3. 运行项目
```bash
cd backend
mvn spring-boot:run
```

默认端口：`8080`

## 4. 返回体
```json
{
  "code": 0,
  "message": "OK",
  "data": {}
}
```

## 5. 认证
- 登录返回 `accessToken + refreshToken`
- 请求头：`Authorization: Bearer <accessToken>`
- access 默认 2 小时，refresh 默认 30 天
- access 过期后调用 `/api/auth/refresh`

## 6. 产品进阶能力（第三优先级）
- 到期提醒：`GET /api/todos/notifications`
- 周期任务：创建/编辑支持 `recurringType + recurringInterval`，完成时自动派生下一条
- 导入导出：`GET /api/todos/export`、`POST /api/todos/import`
- 看板统计：`GET /api/todos/dashboard`
- 多端冲突保护：更新接口支持 `expectedVersion`，冲突返回 `code=4091`

## 7. 认证接口
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/password/reset-code`
- `POST /api/auth/password/reset`
- `POST /api/auth/logout`
- `GET /api/auth/me`

## 8. Todo接口
- `POST /api/todos`
- `PUT /api/todos/{id}`
- `DELETE /api/todos/{id}`
- `GET /api/todos/{id}`
- `GET /api/todos`
- `PATCH /api/todos/{id}/status?done=true&expectedVersion=3`
- `DELETE /api/todos/completed`
- `GET /api/todos/stats`
- `GET /api/todos/dashboard`
- `GET /api/todos/notifications?windowMinutes=1440`
- `GET /api/todos/export`
- `POST /api/todos/import`

### 批量
- `POST /api/todos/bulk/status`
- `POST /api/todos/bulk/delete`

### 排序
- `PATCH /api/todos/reorder`

### 子任务
- `POST /api/todos/{id}/subtasks`
- `PATCH /api/todos/{id}/subtasks/{subtaskId}`
- `DELETE /api/todos/{id}/subtasks/{subtaskId}`

### 回收站
- `GET /api/todos/recycle`
- `POST /api/todos/recycle/{recycleId}/restore`
- `DELETE /api/todos/recycle/{recycleId}`

### 标签/分组
- `GET /api/todos/tags`
- `GET /api/todos/groups`
