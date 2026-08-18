package com.calfus.ragassistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AskRequest {

    @NotBlank(message = "Question is required")
    private String question;

    // Groups this question together with earlier ones from the same
    // conversation, so ChatService can look up chat history for context.
    // The frontend generates a fresh random id per page load (see
    // Dashboard.jsx) and sends it with every question in that session.
    @NotBlank(message = "sessionId is required")
    private String sessionId;
}
