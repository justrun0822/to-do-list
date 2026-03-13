package com.justrun.todo.dto.todo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TodoSubtaskUpdateRequest {
    @Size(max = 120, message = "子任务标题不能超过 120 字")
    private String title;

    @Min(value = 0, message = "子任务状态不合法")
    @Max(value = 1, message = "子任务状态不合法")
    private Integer status;
}
