package com.justrun.todo.dto.todo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TodoNotificationVO {
    private Long taskId;
    private String title;
    private LocalDateTime dueAt;
    private String level;
    private String message;
}
