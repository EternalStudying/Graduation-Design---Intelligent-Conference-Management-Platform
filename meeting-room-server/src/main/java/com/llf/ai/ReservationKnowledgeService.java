package com.llf.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
public class ReservationKnowledgeService {

    private static final List<KnowledgeItem> DEFAULT_ITEMS = List.of(
            new KnowledgeItem("capability_scope", List.of("支持", "范围", "能做什么"), "该助手只处理会议室和预约相关问题，包括空闲会议室查询、会议室推荐、我的预约梳理和时间冲突判断。"),
            new KnowledgeItem("question_guide", List.of("怎么提问", "帮助", "示例"), "提问时最好带上日期、时间段、参会人数和设备需求，例如：今天下午 3 点到 5 点可用的 10 人会议室。")
    );

    private final List<KnowledgeItem> items;

    public ReservationKnowledgeService(ObjectMapper objectMapper) {
        this.items = loadItems(objectMapper);
    }

    public List<String> search(String question) {
        String normalized = question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return DEFAULT_ITEMS.stream().map(KnowledgeItem::content).toList();
        }

        List<ScoredItem> scoredItems = new ArrayList<>();
        for (KnowledgeItem item : items) {
            int score = score(item, normalized);
            if (score > 0) {
                scoredItems.add(new ScoredItem(item, score));
            }
        }

        if (scoredItems.isEmpty()) {
            return DEFAULT_ITEMS.stream().map(KnowledgeItem::content).toList();
        }

        return scoredItems.stream()
                .sorted(Comparator.comparingInt(ScoredItem::score).reversed().thenComparing(entry -> entry.item().id()))
                .limit(3)
                .map(entry -> entry.item().content())
                .toList();
    }

    private List<KnowledgeItem> loadItems(ObjectMapper objectMapper) {
        try (InputStream inputStream = new ClassPathResource("ai/reservation-knowledge.json").getInputStream()) {
            List<KnowledgeItem> loaded = objectMapper.readValue(inputStream, new TypeReference<List<KnowledgeItem>>() {
            });
            if (loaded == null || loaded.isEmpty()) {
                return DEFAULT_ITEMS;
            }
            return loaded;
        } catch (Exception e) {
            return DEFAULT_ITEMS;
        }
    }

    private int score(KnowledgeItem item, String normalizedQuestion) {
        int score = 0;
        for (String keyword : item.keywords()) {
            if (normalizedQuestion.contains(keyword.toLowerCase(Locale.ROOT))) {
                score += 2;
            }
        }
        if (normalizedQuestion.contains(item.id().toLowerCase(Locale.ROOT))) {
            score += 1;
        }
        return score;
    }

    private record ScoredItem(KnowledgeItem item, int score) {
    }

    private record KnowledgeItem(String id, List<String> keywords, String content) {
    }
}
