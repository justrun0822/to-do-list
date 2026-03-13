package com.justrun.todo.dto.todo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class TodoBulkStatusRequest extends TodoBulkRequest {
    @Min(value = 0, message = "任务状态不合法")
    @Max(value = 1, message = "任务状态不合法")
    private Integer status;
}
