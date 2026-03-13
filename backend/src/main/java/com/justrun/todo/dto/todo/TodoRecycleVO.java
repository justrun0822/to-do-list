package com.justrun.todo.dto.todo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TodoRecycleVO {
    private Long id;
    private Long originalTaskId;
    private String title;
    private String groupName;
    private Integer priority;
    private LocalDateTime dueAt;
    private LocalDateTime deletedAt;
}
