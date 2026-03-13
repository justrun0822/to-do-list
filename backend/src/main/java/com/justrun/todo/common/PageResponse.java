package com.justrun.todo.common;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PageResponse<T> {
    private long pageNum;
    private long pageSize;
    private long total;
    private List<T> records;
}
