package com.justrun.todo.dto.todo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TodoStatsVO {
    private long total;
    private long completed;
    private long pending;
}
