package com.justrun.todo.dto.todo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class TodoImportRequest {
    @Valid
    @NotEmpty(message = "导入任务不能为空")
    private List<TodoImportTask> tasks;

    @Data
    public static class TodoImportTask {
        private String title;
        private String description;
        private Integer priority;
        private String groupName;
        private String dueAt;
        private String recurringType;
        private Integer recurringInterval;
        private List<String> tags;
        private List<String> subtasks;
    }
}
