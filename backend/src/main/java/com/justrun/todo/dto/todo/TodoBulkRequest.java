package com.justrun.todo.dto.todo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class TodoBulkRequest {
    @NotEmpty(message = "任务 ID 列表不能为空")
    @Size(max = 300, message = "批量数量不能超过 300")
    private List<Long> ids;
}
