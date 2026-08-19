package com.calfus.ragassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AskResponse {

    private final String answer;
    private final List<SourceSnippet> sources;
}
