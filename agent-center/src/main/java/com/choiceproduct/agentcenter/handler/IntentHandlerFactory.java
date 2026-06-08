package com.choiceproduct.agentcenter.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class IntentHandlerFactory {
    private final Map<String, IntentHandler> handlers = new HashMap<>();

    public IntentHandlerFactory(List<IntentHandler> handlerList) {
        for (IntentHandler handler : handlerList) {
            handlers.put(handler.getHandlerCode(), handler);
        }
    }

    public IntentHandler getHandler(String handlerCode) {
        return handlers.getOrDefault(handlerCode, handlers.get("general_chat"));
    }

    public Map<String, String> listHandlers() {
        Map<String, String> result = new HashMap<>();
        handlers.forEach((code, handler) -> result.put(code, handler.getClass().getSimpleName()));
        return result;
    }
}
