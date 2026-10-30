package com.hmdp.agent.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisChatMemoryTest {

    private List<Message> messages(int count) {
        List<Message> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(i % 2 == 0 ? new UserMessage("u" + i) : new AssistantMessage("a" + i));
        }
        return list;
    }

    @Test
    @DisplayName("超过上限时只保留最近 N 条")
    void trimKeepsLatest() {
        List<Message> trimmed = RedisChatMemory.trim(messages(10), 4);
        assertEquals(4, trimmed.size());
        assertEquals(List.of("u6", "a7", "u8", "a9"),
                trimmed.stream().map(Message::getText).collect(Collectors.toList()));
    }

    @Test
    @DisplayName("未超过上限时原样返回")
    void trimKeepsAll() {
        List<Message> trimmed = RedisChatMemory.trim(messages(3), 20);
        assertEquals(3, trimmed.size());
    }

    @Test
    @DisplayName("上限小于 2 时按 2 处理，保证一问一答成对保留")
    void trimMinimumTwo() {
        assertEquals(2, RedisChatMemory.trim(messages(6), 0).size());
        assertEquals(2, RedisChatMemory.trim(messages(6), -5).size());
    }

    @Test
    @DisplayName("空列表与 null 不抛异常")
    void trimEmpty() {
        assertTrue(RedisChatMemory.trim(new ArrayList<>(), 5).isEmpty());
        assertTrue(RedisChatMemory.trim(null, 5).isEmpty());
    }
}
