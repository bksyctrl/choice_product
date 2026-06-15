package com.ecommerce.workflow.scheduler.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CronExpressionParser {
    
    private static final String CRON_PATTERN = 
        "^([0-9*/-]+)\\s+([0-9*/-]+)\\s+([0-9*/-]+)\\s+([0-9*/-]+)\\s+([0-9*/-]+)(\\s+([0-7*/-]+))?$";
    
    private static final Pattern pattern = Pattern.compile(CRON_PATTERN);
    
    public boolean isValidCron(String cronExpression) {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            return false;
        }
        
        Matcher matcher = pattern.matcher(cronExpression.trim());
        return matcher.matches();
    }
    
    public String parseCronDescription(String cronExpression) {
        if (!isValidCron(cronExpression)) {
            return "无效的Cron表达式";
        }
        
        String[] parts = cronExpression.trim().split("\\s+");
        if (parts.length < 5) {
            return "无效的Cron表达式";
        }
        
        String second = parts[0];
        String minute = parts[1];
        String hour = parts[2];
        String day = parts[3];
        String month = parts[4];
        String week = parts.length > 5 ? parts[5] : "*";
        
        StringBuilder description = new StringBuilder();
        
        if ("*".equals(second) && "*".equals(minute) && "*".equals(hour)) {
            description.append("每秒执行");
        } else if ("*".equals(minute) && "*".equals(hour)) {
            description.append("每分钟的第").append(second).append("秒执行");
        } else if ("*".equals(hour)) {
            description.append("每小时的第").append(minute).append("分").append(second).append("秒执行");
        } else if (!"*".equals(day) && !day.contains("*") && !day.contains("/")) {
            description.append("每月").append(day).append("日");
            description.append(hour).append(":").append(minute).append(":").append(second).append("执行");
        } else if (!"*".equals(week) && !week.contains("*") && !week.contains("/")) {
            String weekDay = getWeekDayName(week);
            description.append("每周").append(weekDay).append(" ");
            description.append(hour).append(":").append(minute).append(":").append(second).append("执行");
        } else if (!"*".equals(month)) {
            description.append("每年").append(getMonthName(month)).append(" ");
            description.append(day.equals("*") ? "每日" : "第" + day + "日").append(" ");
            description.append(hour).append(":").append(minute).append(":").append(second).append("执行");
        } else {
            if (minute.contains("/") && hour.equals("*")) {
                String interval = minute.split("/")[1];
                description.append("每").append(interval).append("分钟执行一次");
            } else if (hour.contains("/") && minute.equals("*")) {
                String interval = hour.split("/")[1];
                description.append("每").append(interval).append("小时执行一次");
            } else if (day.contains("/") && hour.equals("*") && minute.equals("*")) {
                String interval = day.split("/")[1];
                description.append("每").append(interval).append("天执行一次");
            } else {
                description.append("每日");
                description.append(hour).append(":").append(minute).append(":").append(second).append("执行");
            }
        }
        
        return description.toString();
    }
    
    private String getWeekDayName(String week) {
        if (week.contains(",")) {
            String[] days = week.split(",");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < days.length; i++) {
                if (i > 0) sb.append("、");
                sb.append(getSingleWeekDayName(days[i].trim()));
            }
            return sb.toString();
        }
        return getSingleWeekDayName(week);
    }
    
    private String getSingleWeekDayName(String week) {
        return switch (week) {
            case "0", "7" -> "周日";
            case "1" -> "周一";
            case "2" -> "周二";
            case "3" -> "周三";
            case "4" -> "周四";
            case "5" -> "周五";
            case "6" -> "周六";
            default -> "周" + week;
        };
    }
    
    private String getMonthName(String month) {
        if (month.equals("*")) return "每月";
        try {
            int m = Integer.parseInt(month);
            return switch (m) {
                case 1 -> "一月";
                case 2 -> "二月";
                case 3 -> "三月";
                case 4 -> "四月";
                case 5 -> "五月";
                case 6 -> "六月";
                case 7 -> "七月";
                case 8 -> "八月";
                case 9 -> "九月";
                case 10 -> "十月";
                case 11 -> "十一月";
                case 12 -> "十二月";
                default -> month + "月";
            };
        } catch (NumberFormatException e) {
            return month + "月";
        }
    }
    
    public Date getNextExecuteTime(String cronExpression) {
        if (!isValidCron(cronExpression)) {
            return null;
        }
        
        try {
            String[] parts = cronExpression.trim().split("\\s+");
            if (parts.length < 5) {
                return null;
            }
            
            String second = parts[0];
            String minute = parts[1];
            String hour = parts[2];
            String day = parts[3];
            String month = parts[4];
            String week = parts.length > 5 ? parts[5] : null;
            
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime nextTime = calculateNextTime(now, second, minute, hour, day, month, week);
            
            if (nextTime != null) {
                return Date.from(nextTime.atZone(ZoneId.systemDefault()).toInstant());
            }
            
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    private LocalDateTime calculateNextTime(LocalDateTime now, String second, String minute, 
                                            String hour, String day, String month, String week) {
        List<Integer> seconds = parseField(second, 0, 59);
        List<Integer> minutes = parseField(minute, 0, 59);
        List<Integer> hours = parseField(hour, 0, 23);
        List<Integer> days = parseField(day, 1, 31);
        List<Integer> months = parseField(month, 1, 12);
        List<Integer> weekDays = week != null ? parseField(week, 0, 7) : null;
        
        for (int m : months) {
            for (int d : days) {
                if (d > getMaxDaysInMonth(m, now.getYear())) continue;
                
                for (int h : hours) {
                    for (int min : minutes) {
                        for (int sec : seconds) {
                            try {
                                LocalDateTime candidate = LocalDateTime.of(now.getYear(), m, d, h, min, sec);
                                
                                if (weekDays != null && !weekDays.isEmpty()) {
                                    int dayOfWeek = candidate.getDayOfWeek().getValue() % 7;
                                    if (!weekDays.contains(dayOfWeek)) continue;
                                }
                                
                                if (candidate.isAfter(now)) {
                                    return candidate;
                                }
                            } catch (Exception e) {
                                continue;
                            }
                        }
                    }
                }
            }
        }
        
        int nextYear = now.getYear() + 1;
        for (int m : months) {
            for (int d : days) {
                if (d > getMaxDaysInMonth(m, nextYear)) continue;
                
                for (int h : hours) {
                    for (int min : minutes) {
                        for (int sec : seconds) {
                            try {
                                return LocalDateTime.of(nextYear, m, d, h, min, sec);
                            } catch (Exception e) {
                                continue;
                            }
                        }
                    }
                }
            }
        }
        
        return null;
    }
    
    private List<Integer> parseField(String field, int min, int max) {
        List<Integer> values = new ArrayList<>();
        
        if (field.equals("*")) {
            for (int i = min; i <= max; i++) {
                values.add(i);
            }
        } else if (field.contains("/")) {
            String[] parts = field.split("/");
            int start = parts[0].equals("*") ? min : Integer.parseInt(parts[0]);
            int step = Integer.parseInt(parts[1]);
            for (int i = start; i <= max; i += step) {
                values.add(i);
            }
        } else if (field.contains(",")) {
            String[] parts = field.split(",");
            for (String part : parts) {
                values.add(Integer.parseInt(part.trim()));
            }
        } else if (field.contains("-")) {
            String[] parts = field.split("-");
            int start = Integer.parseInt(parts[0]);
            int end = Integer.parseInt(parts[1]);
            for (int i = start; i <= end; i++) {
                values.add(i);
            }
        } else {
            values.add(Integer.parseInt(field));
        }
        
        return values;
    }
    
    private int getMaxDaysInMonth(int month, int year) {
        return switch (month) {
            case 2 -> java.time.Year.isLeap(year) ? 29 : 28;
            case 4, 6, 9, 11 -> 30;
            default -> 31;
        };
    }
    
    public String createCronExpression(String frequency, String time) {
        switch (frequency.toLowerCase()) {
            case "every_minute":
                return "0 * * * * *";
            case "every_hour":
                return "0 0 * * * *";
            case "every_day":
                String[] timeParts = time.split(":");
                return String.format("0 %s %s * * *", timeParts[1], timeParts[0]);
            case "every_week":
                String[] weekParts = time.split(":");
                return String.format("0 %s %s * * %s", weekParts[2], weekParts[1], weekParts[0]);
            case "every_month":
                String[] monthParts = time.split(":");
                return String.format("0 %s %s %s * *", monthParts[2], monthParts[1], monthParts[0]);
            default:
                return "0 0 0 * * *";
        }
    }
    
    public List<LocalDateTime> getNextExecuteTimes(String cronExpression, int count) {
        List<LocalDateTime> times = new ArrayList<>();
        if (!isValidCron(cronExpression)) {
            return times;
        }
        
        LocalDateTime current = LocalDateTime.now();
        for (int i = 0; i < count; i++) {
            Date next = getNextExecuteTime(cronExpression);
            if (next != null) {
                LocalDateTime nextLocal = LocalDateTime.ofInstant(next.toInstant(), ZoneId.systemDefault());
                if (nextLocal.isAfter(current)) {
                    times.add(nextLocal);
                    current = nextLocal.plusSeconds(1);
                }
            }
        }
        
        return times;
    }
}
