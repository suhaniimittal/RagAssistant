// Calls to the document-upload and chat endpoints. Same pattern as
// authApi.js: API_BASE_URL (see ./config.js) decides where the backend
// actually is, and credentials: "include" makes sure the auth cookie rides
// along even on a genuine cross-origin request.

import { API_BASE_URL } from "./config";

export async function uploadDocument(file) {
  const formData = new FormData();
  formData.append("file", file);

  // No manual Content-Type header here -- the browser sets the multipart
  // boundary itself based on the FormData body. Setting it by hand breaks it.
  const response = await fetch(`${API_BASE_URL}/api/documents`, {
    method: "POST",
    credentials: "include",
    body: formData,
  });

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || "Upload failed");
  }
  return response.json(); // { id, filename, status, chunkCount, errorMessage, uploadedAt }
}

export async function listDocuments() {
  const response = await fetch(`${API_BASE_URL}/api/documents`, { credentials: "include" });
  if (!response.ok) {
    throw new Error("Failed to load documents");
  }
  return response.json();
}

// Deletes a document AND its chunks together (see DocumentIngestionService.delete
// on the backend) -- there's nothing left to clean up on the frontend side.
export async function deleteDocument(id) {
  const response = await fetch(`${API_BASE_URL}/api/documents/${id}`, {
    method: "DELETE",
    credentials: "include",
  });
  if (!response.ok) {
    throw new Error("Failed to delete document");
  }
}

// sessionId groups this question together with earlier ones in the same
// conversation, so the backend can look up chat history for follow-up
// questions (see Dashboard.jsx, which generates one per page load).
export async function askQuestion(question, sessionId) {
  const response = await fetch(`${API_BASE_URL}/api/chat/ask`, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ question, sessionId }),
  });

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || "Failed to get an answer");
  }
  return response.json(); // { answer, sources: [{ filename, pageNumber, text, score }] }
}
