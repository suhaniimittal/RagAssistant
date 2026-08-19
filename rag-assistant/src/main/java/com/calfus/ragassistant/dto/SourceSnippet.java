package com.calfus.ragassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** One retrieved chunk cited in an answer -- what the frontend shows under "Sources". */
@Getter
@AllArgsConstructor
public class SourceSnippet {

    private final String filename;
    private final Integer pageNumber;
    private final String text;
    private final double score;
}