package com.justrun.todo.dto.todo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TodoCreateRequest {
    @NotBlank(message = "任务标题不能为空")
    @Size(max = 120, message = "任务标题不能超过 120 字")
    private String title;

    @Size(max = 1000, message = "任务描述不能超过 1000 字")
    private String description;

    @Min(value = 1, message = "优先级最小为 1")
    @Max(value = 3, message = "优先级最大为 3")
    private Integer priority = 2;

    @Size(max = 64, message = "分组名不能超过 64 字")
    private String groupName;

    private LocalDateTime dueAt;

    private List<String> tags;

    @Pattern(regexp = "^(NONE|DAILY|WEEKLY)$", message = "周期类型不合法")
    private String recurringType = "NONE";

    @Min(value = 1, message = "周期最小为 1")
    @Max(value = 30, message = "周期最大为 30")
    private Integer recurringInterval = 1;
}
