import { useEffect, useRef, useState } from "react";
import { useAuth } from "../context/AuthContext";
import { askQuestion, deleteDocument, listDocuments, uploadDocument } from "../api/ragApi";

// Documents whose ingestion might still be running when the page loads --
// poll briefly so "Processing..." flips to "Ready" without a manual refresh.
const PROCESSING_POLL_MS = 3000;

// Small inline SVG icons -- kept as plain functions (not a separate file)
// since they're only used here. Using real icons instead of emoji keeps the
// UI looking consistent across every operating system/browser.
function IconUpload() {
  return (
    <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 16V4M12 4l-4 4M12 4l4 4" />
      <path d="M4 16v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" />
    </svg>
  );
}

function IconFile() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <path d="M14 2v6h6" />
    </svg>
  );
}

function IconLogout() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
      <path d="M16 17l5-5-5-5" />
      <path d="M21 12H9" />
    </svg>
  );
}

function IconSend() {
  return (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M22 2L11 13" />
      <path d="M22 2l-7 20-4-9-9-4 20-7z" />
    </svg>
  );
}

function IconTrash() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 6h18" />
      <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
      <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
      <path d="M10 11v6M14 11v6" />
    </svg>
  );
}

function IconChat() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
    </svg>
  );
}

// Chevron used for both the file-row and per-page collapse toggles -- CSS
// rotates it (see .chat-source-chevron / .chat-source-chevron-expanded in
// App.css): pointing down when collapsed, up when expanded.
function IconChevron() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M9 18l6-6-6-6" />
    </svg>
  );
}

// Bolds anything in a chunk's text that LOOKS like a key fact worth drawing
// the eye to: a currency amount (Rs.2000/-, ₹2,000, $100), a number+unit
// duration (3 months, 10 days), or a percentage (25%). This is a pattern-based
// guess, not a guarantee -- it highlights every match it finds throughout the
// chunk, not specifically the one fact an answer happened to cite.
const HIGHLIGHT_REGEX =
  /((?:₹|Rs\.?\s?|\$)\s?[\d,]+(?:\.\d+)?\/?-?|\b\d+(?:\.\d+)?\s?(?:days?|months?|years?|weeks?|hours?)\b|\b\d+(?:\.\d+)?%)/gi;

function highlightKeyFacts(text) {
  const parts = text.split(HIGHLIGHT_REGEX);
  return parts.map((part, index) =>
    index % 2 === 1 ? (
      <strong key={index} className="chat-source-highlight">
        {part}
      </strong>
    ) : (
      part
    )
  );
}

// Turns the flat list of matched chunks into one group per file, e.g.
// policy.pdf's 4 matched pages become a single { filename, pages: [...] }
// entry -- this is what lets the UI show "policy.pdf -- 4 pages" as one row
// instead of 4 separate flat cards.
function groupSourcesByFile(sources) {
  const groups = [];
  const groupByFilename = new Map();
  for (const source of sources) {
    let group = groupByFilename.get(source.filename);
    if (!group) {
      group = { filename: source.filename, pages: [] };
      groupByFilename.set(source.filename, group);
      groups.push(group);
    }
    group.pages.push(source);
  }
  return groups;
}

// One message's Sources block: a count header, one row per file (each
// showing a "N pages" pill), expandable to reveal that file's matched pages.
// The file with the most matched pages auto-expands first, since it's
// usually the most relevant one to the question. Kept as its own component
// (rather than inline in the message loop below) so each message gets its
// own independent expand state for free, just by being a separate component
// instance per message.
function SourcesPanel({ sources }) {
  const fileGroups = groupSourcesByFile(sources);
  const defaultExpandedFilename = fileGroups.reduce(
    (best, group) => (group.pages.length > best.pages.length ? group : best),
    fileGroups[0]
  ).filename;

  const [expandedFilename, setExpandedFilename] = useState(defaultExpandedFilename);
  const [collapsedPageKeys, setCollapsedPageKeys] = useState(() => new Set());

  function togglePage(pageKey) {
    setCollapsedPageKeys((prev) => {
      const next = new Set(prev);
      if (next.has(pageKey)) {
        next.delete(pageKey);
      } else {
        next.add(pageKey);
      }
      return next;
    });
  }

  return (
    <div className="chat-sources">
      <p className="chat-sources-label">
        Sources <span className="chat-sources-count">{sources.length}</span>
      </p>
      <div className="chat-sources-file-list">
        {fileGroups.map((group) => {
          const isFileExpanded = expandedFilename === group.filename;
          return (
            <div key={group.filename} className="chat-source-file-group">
              <button
                type="button"
                className="chat-source-file-row"
                onClick={() => setExpandedFilename(isFileExpanded ? null : group.filename)}
                aria-expanded={isFileExpanded}
              >
                <span className="chat-source-file-icon">
                  <IconFile />
                </span>
                <span className="chat-source-file" title={group.filename}>
                  {group.filename}
                </span>
                <span className="chat-source-page-count">
                  {group.pages.length} page{group.pages.length === 1 ? "" : "s"}
                </span>
                <span className={`chat-source-chevron${isFileExpanded ? " chat-source-chevron-expanded" : ""}`}>
                  <IconChevron />
                </span>
              </button>

              {isFileExpanded && (
                <div className="chat-source-page-list">
                  {group.pages.map((page, pageIndex) => {
                    const pageKey = `${group.filename}|${page.pageNumber}|${pageIndex}`;
                    const isPageCollapsed = collapsedPageKeys.has(pageKey);
                    return (
                      <div key={pageKey} className="chat-source-page-item">
                        <div className="chat-source-page-badge">{page.pageNumber ?? "?"}</div>
                        {!isPageCollapsed && (
                          <div className="chat-source-page-text-wrap">
                            <p className="chat-source-page-text">{highlightKeyFacts(page.text)}</p>
                          </div>
                        )}
                        <button
                          type="button"
                          className={`chat-source-chevron chat-source-page-chevron${
                            isPageCollapsed ? "" : " chat-source-chevron-expanded"
                          }`}
                          onClick={() => togglePage(pageKey)}
                          aria-label={isPageCollapsed ? "Show excerpt" : "Hide excerpt"}
                        >
                          <IconChevron />
                        </button>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

export default function Dashboard() {
  const { user, logout } = useAuth();

  const [documents, setDocuments] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState("");
  const [deletingId, setDeletingId] = useState(null);
  const fileInputRef = useRef(null);

  const [messages, setMessages] = useState([]); // { role: "user" | "assistant", text, sources? }
  const [question, setQuestion] = useState("");
  const [asking, setAsking] = useState(false);
  const messagesEndRef = useRef(null);

  // Groups every question asked during this page visit into one
  // conversation, so the backend can look up earlier turns and understand
  // follow-up questions ("what about for contractors?"). Generated ONCE per
  // page load with useRef (not useState) since it never needs to change or
  // trigger a re-render -- refreshing the page starts a new conversation.
  const sessionIdRef = useRef(crypto.randomUUID());

  useEffect(() => {
    refreshDocuments();
  }, []);

  // While anything is still PROCESSING, keep checking until it settles into
  // READY/FAILED -- ingestion (parsing + OCR + embeddings) isn't instant.
  useEffect(() => {
    const hasProcessing = documents.some((doc) => doc.status === "PROCESSING");
    if (!hasProcessing) return;
    const timer = setTimeout(refreshDocuments, PROCESSING_POLL_MS);
    return () => clearTimeout(timer);
  }, [documents]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  async function refreshDocuments() {
    try {
      const docs = await listDocuments();
      setDocuments(docs);
    } catch {
      // Document list just stays as-is -- not worth blocking the whole page over.
    }
  }

  async function handleFileChange(event) {
    const file = event.target.files[0];
    if (!file) return;

    setUploading(true);
    setUploadError("");
    try {
      await uploadDocument(file);
      await refreshDocuments();
    } catch (err) {
      setUploadError(err.message);
    } finally {
      setUploading(false);
      event.target.value = ""; // lets the same filename be picked again later
    }
  }

  // Confirms first (this is permanent -- both the document row AND its
  // Qdrant chunks are gone for good, see DocumentIngestionService.delete on
  // the backend), then removes it from the list once the backend confirms.
  async function handleDeleteDocument(doc) {
    const confirmed = window.confirm(`Delete "${doc.filename}"? This removes it and all its chunks permanently.`);
    if (!confirmed) return;

    setDeletingId(doc.id);
    try {
      await deleteDocument(doc.id);
      await refreshDocuments();
    } catch (err) {
      window.alert(err.message);
    } finally {
      setDeletingId(null);
    }
  }

  async function handleAsk(event) {
    event.preventDefault();
    const trimmed = question.trim();
    if (!trimmed || asking) return;

    setMessages((prev) => [...prev, { role: "user", text: trimmed }]);
    setQuestion("");
    setAsking(true);
    try {
      const response = await askQuestion(trimmed, sessionIdRef.current);
      setMessages((prev) => [
        ...prev,
        { role: "assistant", text: response.answer, sources: response.sources },
      ]);
    } catch (err) {
      setMessages((prev) => [...prev, { role: "assistant", text: `Error: ${err.message}` }]);
    } finally {
      setAsking(false);
    }
  }

  const hasReadyDocument = documents.some((doc) => doc.status === "READY");
  const initial = (user?.identifier || "?").trim().charAt(0).toUpperCase();

  return (
    <div className="chat-page">
      <aside className="chat-sidebar">
        <div className="sidebar-brand">
          <div className="brand-icon">
            <IconChat />
          </div>
          <span className="brand-name">DocuChat</span>
        </div>

        <div className="chat-sidebar-header">
          <div className="user-avatar">{initial}</div>
          <div className="user-info">
            <p className="chat-user" title={user?.identifier}>
              {user?.identifier}
            </p>
            <button className="logout-button" onClick={logout}>
              <IconLogout />
              Log out
            </button>
          </div>
        </div>

        <div className="sidebar-section">
          <h2>Your documents</h2>

          <label className={`upload-dropzone${uploading ? " upload-dropzone-busy" : ""}`}>
            <div className="upload-icon">
              <IconUpload />
            </div>
            <span className="upload-title">{uploading ? "Uploading…" : "Upload a PDF"}</span>
            <span className="upload-hint">Click to browse your files</span>
            <input
              ref={fileInputRef}
              type="file"
              accept="application/pdf"
              onChange={handleFileChange}
              disabled={uploading}
              hidden
            />
          </label>
          {uploadError && <p className="field-error">{uploadError}</p>}

          <ul className="document-list">
            {documents.length === 0 && (
              <li className="document-empty">Nothing uploaded yet — your PDFs will show up here.</li>
            )}
            {documents.map((doc) => (
              <li key={doc.id} className="document-item">
                <div className="document-icon">
                  <IconFile />
                </div>
                <div className="document-info">
                  <span className="document-name" title={doc.filename}>
                    {doc.filename}
                  </span>
                  <span className={`document-status document-status-${doc.status.toLowerCase()}`}>
                    <span className="status-dot" />
                    {doc.status === "PROCESSING" ? "Processing…" : doc.status === "READY" ? "Ready" : "Failed"}
                  </span>
                  {doc.status === "FAILED" && doc.errorMessage && (
                    <span className="document-error">{doc.errorMessage}</span>
                  )}
                </div>
                <button
                  type="button"
                  className="document-delete-button"
                  onClick={() => handleDeleteDocument(doc)}
                  disabled={deletingId === doc.id}
                  aria-label={`Delete ${doc.filename}`}
                  title={`Delete ${doc.filename}`}
                >
                  <IconTrash />
                </button>
              </li>
            ))}
          </ul>
        </div>
      </aside>

      <main className="chat-main">
        <header className="chat-header">
          <h1>Ask about your documents</h1>
          <p>
            {documents.length === 0
              ? "No documents uploaded yet"
              : `${documents.length} document${documents.length === 1 ? "" : "s"} uploaded`}
          </p>
        </header>

        <div className="chat-messages">
          {messages.length === 0 && (
            <div className="chat-empty">
              <div className="chat-empty-icon">
                <IconChat />
              </div>
              <p>
                {hasReadyDocument
                  ? "Ask a question about your uploaded documents."
                  : "Upload a PDF on the left, then ask a question about it here."}
              </p>
            </div>
          )}

          {messages.map((message, index) => (
            <div key={index} className={`chat-bubble chat-bubble-${message.role}`}>
              <p className="chat-bubble-text">
                {message.role === "assistant" ? highlightKeyFacts(message.text) : message.text}
              </p>
              {message.sources && message.sources.length > 0 && (
                <SourcesPanel sources={message.sources} />
              )}
            </div>
          ))}
          <div ref={messagesEndRef} />
        </div>

        <form className="chat-input-row" onSubmit={handleAsk}>
          <input
            type="text"
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            placeholder="Ask a question about your documents..."
            disabled={asking}
          />
          <button type="submit" disabled={asking || question.trim() === ""}>
            {asking ? "Thinking…" : (
              <>
                Send <IconSend />
              </>
            )}
          </button>
        </form>
      </main>
    </div>
  );
}
