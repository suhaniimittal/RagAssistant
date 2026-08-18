package com.calfus.ragassistant.service;

import com.calfus.ragassistant.dto.AskResponse;
import com.calfus.ragassistant.dto.SourceSnippet;
import com.calfus.ragassistant.model.ChatHistory;
import com.calfus.ragassistant.model.User;
import com.calfus.ragassistant.repository.ChatHistoryRepository;
import com.calfus.ragassistant.repository.UserRepository;
import com.calfus.ragassistant.vectorstore.QdrantClient;
import com.calfus.ragassistant.vectorstore.QdrantSearchResult;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * This is the "answer a question" half of the app (uploading/chunking PDFs
 * is DocumentIngestionService's job -- this class only reads what's already
 * been stored).
 *
 * Kept deliberately simple and cheap: exactly ONE OpenAI embedding call and
 * ONE OpenAI chat call per question -- no separate query-rewrite call, no
 * keyword search + merge, no rerank call. An earlier version of this class
 * did all of that (matching a more "textbook" RAG architecture), but it
 * tripled the OpenAI calls (and cost/latency) per question for a small
 * student project where the simple version already works well.
 *
 * Conversation memory is still real, though: earlier turns from the SAME
 * session are loaded from Postgres and included as plain text in the SAME
 * final prompt (not a separate LLM call), so follow-up questions like
 * "what about for contractors?" still get some context.
 *
 * Steps:
 *   1. Ask + Memory -- load earlier turns from this conversation (a DB read,
 *      not an OpenAI call).
 *   2. Embed the question and run ONE vector search in Qdrant, scoped to
 *      this user only.
 *   3. Build one prompt containing the recent conversation + the matched
 *      document excerpts, and ask the chat model for an answer.
 *   4. Return the answer with its sources, and save this turn to history.
 */
@Service
public class ChatService {

    private static final int TOP_K = 5;                  // how many chunks the vector search fetches
    private static final int SNIPPET_MAX_LENGTH = 300;    // trimmed source text shown in the UI
    private static final int MAX_HISTORY_TURNS = 4;       // how many earlier turns are shown to the model

    private final EmbeddingModel embeddingModel;
    private final ChatLanguageModel chatModel;
    private final QdrantClient qdrantClient;
    private final ChatHistoryRepository chatHistoryRepository;
    private final UserRepository userRepository;

    public ChatService(
            EmbeddingModel embeddingModel,
            ChatLanguageModel chatModel,
            QdrantClient qdrantClient,
            ChatHistoryRepository chatHistoryRepository,
            UserRepository userRepository) {
        this.embeddingModel = embeddingModel;
        this.chatModel = chatModel;
        this.qdrantClient = qdrantClient;
        this.chatHistoryRepository = chatHistoryRepository;
        this.userRepository = userRepository;
    }

    public AskResponse ask(UUID userId, String sessionId, String question) {

        // Step 1: Ask + Memory -- load this conversation's earlier turns (just a DB read, no OpenAI call).
        List<ChatHistory> history = loadRecentHistory(sessionId, userId);

        // Step 2: embed the question (1st OpenAI call) and run ONE vector search.
        float[] questionVectorAsArray = embeddingModel.embed(question).content().vector();
        List<Float> questionVector = toFloatList(questionVectorAsArray);
        List<QdrantSearchResult> matches = qdrantClient.search(questionVector, TOP_K, userId);

        if (matches.isEmpty()) {
            String noDataAnswer = "I couldn't find anything in your uploaded documents to answer that. "
                    + "Try uploading a relevant PDF first, or rephrasing your question.";
            saveTurn(userId, sessionId, question, noDataAnswer);
            return new AskResponse(noDataAnswer, new ArrayList<>());
        }

        // Step 3: build the context text and the sources list from the matched chunks.
        StringBuilder contextText = new StringBuilder();
        List<SourceSnippet> sources = new ArrayList<>();

        for (QdrantSearchResult match : matches) {
            String filename = String.valueOf(match.payload().get("filename"));
            String fullChunkText = String.valueOf(match.payload().get("text"));
            Integer pageNumber = toPageNumber(match.payload().get("pageNumber"));

            if (contextText.length() > 0) {
                contextText.append("\n\n---\n\n");
            }
            contextText.append("Source: ").append(filename)
                    .append(" (page ").append(pageNumber).append(")\n")
                    .append(fullChunkText);

            String shortChunkText = truncate(fullChunkText, SNIPPET_MAX_LENGTH);
            sources.add(new SourceSnippet(filename, pageNumber, shortChunkText, match.score()));
        }

        // Step 4: ONE prompt with both the recent conversation AND the matched
        // excerpts -- this is what lets follow-up questions still work without
        // a separate rewrite call first.
        String historyText = buildHistoryText(history);

        String prompt = """
                You are a helpful assistant answering questions using ONLY the document excerpts below.
                If the excerpts don't contain the answer, say you don't know rather than guessing.
                %s
                Excerpts:
                %s

                Question: %s

                Answer clearly, and mention which document(s) the answer came from.
                """.formatted(historyText, contextText.toString(), question);

        String answer = chatModel.generate(prompt); // 2nd (and last) OpenAI call for this question

        saveTurn(userId, sessionId, question, answer);

        return new AskResponse(answer, sources);
    }

    // -------------------------------------------------------------------
    // Ask + Memory
    // -------------------------------------------------------------------

    private List<ChatHistory> loadRecentHistory(String sessionId, UUID userId) {
        List<ChatHistory> allTurns = chatHistoryRepository.findBySessionIdAndUserIdOrderByCreatedAtAsc(sessionId, userId);
        if (allTurns.size() <= MAX_HISTORY_TURNS) {
            return allTurns;
        }
        // Only keep the most recent turns -- older ones matter less for
        // understanding a follow-up question, and this keeps the prompt from
        // growing without bound the longer a conversation goes on.
        return allTurns.subList(allTurns.size() - MAX_HISTORY_TURNS, allTurns.size());
    }

    // Turns the earlier turns into plain text to paste into the prompt.
    // Returns "" (not null) when there's no history yet, so .formatted()
    // above just leaves that spot blank instead of printing "null".
    private String buildHistoryText(List<ChatHistory> history) {
        if (history.isEmpty()) {
            return "";
        }

        StringBuilder historyText = new StringBuilder();
        historyText.append("Recent conversation (use this to understand follow-up questions):\n");
        for (ChatHistory turn : history) {
            historyText.append("User: ").append(turn.getQuestion()).append("\n");
            historyText.append("Assistant: ").append(turn.getAnswer()).append("\n");
        }
        historyText.append("\n");
        return historyText.toString();
    }

    private void saveTurn(UUID userId, String sessionId, String question, String answer) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return; // shouldn't happen for an authenticated request, but don't crash the chat over it
        }

        ChatHistory turn = new ChatHistory();
        turn.setUser(user);
        turn.setSessionId(sessionId);
        turn.setQuestion(question);
        turn.setAnswer(answer);
        chatHistoryRepository.save(turn);
    }

    // -------------------------------------------------------------------
    // Small shared helpers
    // -------------------------------------------------------------------

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    // Qdrant sends the pageNumber back to us as JSON, and Java isn't 100%
    // sure if that JSON number should become an Integer or a Long -- so this
    // method just accepts "any kind of Number" and converts it to a plain
    // Integer. If pageNumber is missing for some reason, we return null
    // instead of crashing.
    private Integer toPageNumber(Object value) {
        if (value instanceof Number) {
            Number numberValue = (Number) value;
            return numberValue.intValue();
        }
        return null;
    }

    // The embedding model gives us a float[] (a fixed-size array of numbers).
    // Our QdrantClient wants a List<Float> instead (so it can turn it into
    // JSON easily). This just copies each number across into a List.
    private List<Float> toFloatList(float[] vector) {
        List<Float> list = new ArrayList<>();
        for (float value : vector) {
            list.add(value);
        }
        return list;
    }
}
