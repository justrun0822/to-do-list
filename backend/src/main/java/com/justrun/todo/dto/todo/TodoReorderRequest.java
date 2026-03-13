package com.justrun.todo.dto.todo;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class TodoReorderRequest {
    @NotEmpty(message = "排序 ID 列表不能为空")
    private List<Long> orderedIds;
}
