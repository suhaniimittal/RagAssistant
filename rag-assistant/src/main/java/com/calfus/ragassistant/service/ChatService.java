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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@RequiredArgsConstructor
@Service
public class ChatService {

    private static final int TOP_K = 18;
    private static final int MAX_HISTORY_TURNS = 4;       // how many earlier turns are shown to the model

    private static final double RELEVANCE_GAP = 0.15;

    private static final String SMALL_TALK_TAG = "TYPE: SMALL_TALK";
    private static final String DOCUMENT_TAG = "TYPE: DOCUMENT";

    private final EmbeddingModel embeddingModel;
    private final ChatLanguageModel chatModel;
    private final QdrantClient qdrantClient;
    private final ChatHistoryRepository chatHistoryRepository;
    private final UserRepository userRepository;

    public AskResponse ask(UUID userId, String sessionId, String question) {

        // Step 1: Ask + Memory -- load this conversation's earlier turns (just a DB read, no OpenAI call).
        List<ChatHistory> history = loadRecentHistory(sessionId, userId);

        // Step 2: embed the question (1st OpenAI call) and run ONE vector search.
        // normalizeQuestion() only affects what gets embedded/searched -- the
        // raw "question" (as the user actually typed it) is still what's saved
        // to history and what the LLM sees in the final prompt below.
        String normalizedQuestion = normalizeQuestion(question);
        float[] questionVectorAsArray = embeddingModel.embed(normalizedQuestion).content().vector();
        List<Float> questionVector = toFloatList(questionVectorAsArray);
        List<QdrantSearchResult> matches = qdrantClient.search(questionVector, TOP_K, userId);

        if (matches.isEmpty()) {
            String noDataAnswer = "I couldn't find anything in your uploaded documents to answer that. "
                    + "Try uploading a relevant PDF first, or rephrasing your question.";
            saveTurn(userId, sessionId, question, noDataAnswer);
            return new AskResponse(noDataAnswer, new ArrayList<>());
        }

        // Drop chunks that only made the top-18 to pad it out, not because
        // they're actually relevant to THIS question (see RELEVANCE_GAP
        // above). The best match always survives this filter by definition,
        // so relevantMatches is never empty here.
        double bestScore = matches.stream().mapToDouble(QdrantSearchResult::score).max().orElse(0);
        List<QdrantSearchResult> relevantMatches = matches.stream()
                .filter(match -> match.score() >= bestScore - RELEVANCE_GAP)
                .toList();

        // Step 3: build the context text and the sources list from the matched chunks.
        StringBuilder contextText = new StringBuilder();
        List<SourceSnippet> sources = new ArrayList<>();
        Set<String> citedPages = new HashSet<>();

        for (QdrantSearchResult match : relevantMatches) {
            String filename = String.valueOf(match.payload().get("filename"));
            String fullChunkText = String.valueOf(match.payload().get("text"));
            Integer pageNumber = toPageNumber(match.payload().get("pageNumber"));

            if (contextText.length() > 0) {
                contextText.append("\n\n---\n\n");
            }
            contextText.append("Source: ").append(filename)
                    .append(" (page ").append(pageNumber).append(")\n")
                    .append(fullChunkText);

            // The UI only ever shows filename + page number for a source (see
            // Dashboard.jsx), never this text -- it's kept on SourceSnippet
            // only in case something needs it later (e.g. a "view excerpt"
            // feature), so no truncation logic is needed here at all.
            String citationKey = filename + "|" + pageNumber;
            if (citedPages.add(citationKey)) {
                sources.add(new SourceSnippet(filename, pageNumber, fullChunkText, match.score()));
            }
        }

        // Step 4: ONE prompt with both the recent conversation AND the matched
        // excerpts -- this is what lets follow-up questions still work without
        // a separate rewrite call first.
        String historyText = buildHistoryText(history);

        String prompt = """
                You are a helpful assistant answering questions using ONLY the document excerpts below.

                First, decide what kind of message this is:
                - SMALL_TALK: a greeting, thanks, or casual remark (e.g. "hi", "how are you doing???",
                  "thanks", "bye") that is NOT actually asking about the documents.
                - DOCUMENT: a genuine question about the documents.

                Start your reply with exactly one line containing ONLY "TYPE: SMALL_TALK" or
                "TYPE: DOCUMENT" (nothing else on that line), then a blank line, then your answer.

                If SMALL_TALK: ignore the excerpts below entirely and just reply naturally and
                warmly in 1-2 short sentences, inviting the user to ask about their documents.
                Do NOT say "I don't know" to small talk.

                If DOCUMENT: answer using ONLY the excerpts below. If the excerpts don't contain
                the answer, say you don't know rather than guessing. If different excerpts
                (especially from different documents) give different or conflicting information,
                do NOT silently pick one -- say so explicitly and state what each document says,
                naming each source file. You may refer to a document by name if it reads naturally,
                but do NOT list or enumerate specific page numbers in your answer -- the app shows
                the exact pages used in a separate Sources panel, so restating them in your answer
                risks not matching that list exactly.

                In both cases, write the answer itself as plain text only -- no Markdown syntax
                at all (no **bold**, no numbered "1." or bulleted "-" lists, no headings). Use
                plain sentences, and separate distinct points with a line break instead of a list.
                %s
                Excerpts:
                %s

                Question: %s
                """.formatted(historyText, contextText.toString(), question);

        String rawAnswer = chatModel.generate(prompt); // 2nd (and last) OpenAI call for this question

        // The model's classification (see the tags above) decides whether the
        // sources panel makes sense to show at all -- for small talk, sources
        // is forced to an empty list even though matches/contextText above
        // did technically retrieve chunks (we don't know it's small talk
        // until this same call comes back, so retrieval still runs first).
        boolean isSmallTalk = rawAnswer.trim().regionMatches(true, 0, SMALL_TALK_TAG, 0, SMALL_TALK_TAG.length());
        String answer = stripTypeTag(rawAnswer);

        saveTurn(userId, sessionId, question, answer);

        return new AskResponse(answer, isSmallTalk ? new ArrayList<>() : sources);
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

    // Trims stray leading/trailing whitespace, collapses repeated inner spaces,
    // and lowercases the question before it's turned into a vector. Doesn't
    // change what the model understands (embedding models already handle case
    // and typos reasonably well) -- this is just about removing pointless
    // differences between e.g. "WHAT is the probation period" and "what is
    // the probation period  " so they embed as closer to the same vector,
    // instead of the search's top-5 cutoff shuffling around for no real reason.
    private String normalizeQuestion(String question) {
        return question.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    // Removes the leading "TYPE: ..." classification line (see SMALL_TALK_TAG/
    // DOCUMENT_TAG above) so the user never sees it -- only the actual answer
    // that follows it. Falls back to returning the raw text untouched if the
    // model ever forgets to include the tag, so a formatting slip on the
    // model's end never breaks the chat.
    private String stripTypeTag(String rawAnswer) {
        String trimmed = rawAnswer.trim();
        boolean hasTag = trimmed.regionMatches(true, 0, SMALL_TALK_TAG, 0, SMALL_TALK_TAG.length())
                || trimmed.regionMatches(true, 0, DOCUMENT_TAG, 0, DOCUMENT_TAG.length());
        if (!hasTag) {
            return trimmed;
        }

        int newlineIndex = trimmed.indexOf('\n');
        return newlineIndex == -1 ? trimmed : trimmed.substring(newlineIndex + 1).trim();
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
