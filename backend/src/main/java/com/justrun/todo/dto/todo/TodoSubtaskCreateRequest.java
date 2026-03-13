package com.justrun.todo.dto.todo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TodoSubtaskCreateRequest {
    @NotBlank(message = "子任务标题不能为空")
    @Size(max = 120, message = "子任务标题不能超过 120 字")
    private String title;
}
