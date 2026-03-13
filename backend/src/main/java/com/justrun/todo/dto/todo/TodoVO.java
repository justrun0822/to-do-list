package com.justrun.todo.dto.todo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class TodoVO {
    private Long id;
    private String title;
    private String description;
    private Integer status;
    private Integer priority;
    private String groupName;
    private Integer sortOrder;
    private LocalDateTime dueAt;
    private String recurringType;
    private Integer recurringInterval;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<String> tags = new ArrayList<>();
    private List<TodoSubtaskVO> subtasks = new ArrayList<>();
}
