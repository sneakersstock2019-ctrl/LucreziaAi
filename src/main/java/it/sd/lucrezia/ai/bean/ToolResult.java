package it.sd.lucrezia.ai.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult<T> {

    private boolean success;
    private String message;
    private String nextAction;
    private T data;

    public static <T> ToolResult<T> ok(String message, String nextAction, T data) {
        return new ToolResult<>(true, message, nextAction, data);
    }

    public static <T> ToolResult<T> error(String message, String nextAction, T data) {
        return new ToolResult<>(false, message, nextAction, data);
    }
}