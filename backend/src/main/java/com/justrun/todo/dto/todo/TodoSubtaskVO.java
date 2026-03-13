package com.justrun.todo.dto.todo;

import lombok.Data;

@Data
public class TodoSubtaskVO {
    private Long id;
    private Long taskId;
    private String title;
    private Integer status;
    private Integer sortOrder;
}
