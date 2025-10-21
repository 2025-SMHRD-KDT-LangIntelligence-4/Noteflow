package com.smhrd.web.dto;

import java.util.List;

public class EmbedRequest {
    private List<String> texts;

    public EmbedRequest(List<String> texts) {
        this.texts = texts;
    }

    public List<String> getTexts() {
        return texts;
    }
}