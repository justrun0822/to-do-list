package com.justrun.todo.dto.todo;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class TodoDashboardVO {
    private long total;
    private long completed;
    private long pending;
    private long overdue;
    private long dueSoon;
    private long completedLast7Days;
    private Map<Integer, Long> priorityDistribution;
    private List<DailyCompletionPoint> dailyCompletion;

    @Data
    public static class DailyCompletionPoint {
        private String day;
        private long completed;
    }
}
