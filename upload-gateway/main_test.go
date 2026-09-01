package main

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strconv"
	"strings"
	"testing"
	"time"
)

func testGateway(t *testing.T) (*server, http.Handler) {
	t.Helper()
	s := &server{cfg: configuration{
		dataDir:      t.TempDir(),
		token:        "test-token",
		publicBase:   "https://files.example",
		maxBytes:     1024,
		retention:    time.Hour,
		referenceTTL: 5 * time.Minute,
	}}
	return s, newHandler(s)
}

func authorizedRequest(method, target string, body io.Reader) *http.Request {
	r := httptest.NewRequest(method, target, body)
	r.Header.Set("Authorization", "Bearer test-token")
	return r
}

func validUploadBody(content []byte) []byte {
	digest := sha256.Sum256(content)
	body, _ := json.Marshal(createRequest{
		UploadID:     "upload-1",
		AttemptID:    "attempt-1",
		AttachmentID: "attachment-1",
		ProviderID:   "openrouter",
		ModelID:      "model-1",
		MIMEType:     "text/plain",
		ContentType:  "DOCUMENT",
		SHA256:       hex.EncodeToString(digest[:]),
		ByteCount:    int64(len(content)),
	})
	return body
}

func serve(handler http.Handler, request *http.Request) *httptest.ResponseRecorder {
	recorder := httptest.NewRecorder()
	handler.ServeHTTP(recorder, request)
	return recorder
}

func TestSessionMetadataRoundTripsEveryOwnerField(t *testing.T) {
	s, _ := testGateway(t)
	want := session{
		UploadID: "upload-1", AttemptID: "attempt-1", AttachmentID: "attachment-1",
		ProviderID: "openrouter", ModelID: "model-1", MIMEType: "text/plain",
		ContentType: "DOCUMENT", SHA256: strings.Repeat("a", 64), ByteCount: 5,
		TokenFingerprint: tokenFingerprint(s.cfg.token), CreatedAt: time.Now().UTC().Truncate(time.Second),
		ExpiresAt: time.Now().UTC().Add(time.Hour).Truncate(time.Second), Completed: true,
	}
	if err := s.save(want); err != nil {
		t.Fatal(err)
	}
	got, err := s.load(want.UploadID)
	if err != nil {
		t.Fatal(err)
	}
	if got != want {
		t.Fatalf("metadata round trip mismatch:\nwant %#v\n got %#v", want, got)
	}
}

func TestUploadLifecycleIsAuthenticatedResumableAndHashVerified(t *testing.T) {
	content := []byte("hello")
	_, handler := testGateway(t)

	created := serve(handler, authorizedRequest(http.MethodPost, "/v1/attachments", bytes.NewReader(validUploadBody(content))))
	if created.Code != http.StatusCreated {
		t.Fatalf("create status=%d body=%s", created.Code, created.Body.String())
	}

	head := serve(handler, authorizedRequest(http.MethodHead, "/v1/attachments/upload-1", nil))
	if head.Code != http.StatusNoContent || head.Header().Get("Upload-Offset") != "0" {
		t.Fatalf("head status=%d offset=%q", head.Code, head.Header().Get("Upload-Offset"))
	}

	appendRequest := authorizedRequest(http.MethodPatch, "/v1/attachments/upload-1", bytes.NewReader(content))
	appendRequest.Header.Set("Upload-Offset", "0")
	appended := serve(handler, appendRequest)
	if appended.Code != http.StatusNoContent || appended.Header().Get("Upload-Offset") != "5" {
		t.Fatalf("append status=%d offset=%q body=%s", appended.Code, appended.Header().Get("Upload-Offset"), appended.Body.String())
	}

	completed := serve(handler, authorizedRequest(http.MethodPost, "/v1/attachments/upload-1/complete", nil))
	if completed.Code != http.StatusOK {
		t.Fatalf("complete status=%d body=%s", completed.Code, completed.Body.String())
	}
	var result struct {
		URL string `json:"url"`
	}
	if err := json.Unmarshal(completed.Body.Bytes(), &result); err != nil {
		t.Fatal(err)
	}
	reference, err := url.Parse(result.URL)
	if err != nil {
		t.Fatal(err)
	}
	downloaded := serve(handler, httptest.NewRequest(http.MethodGet, reference.RequestURI(), nil))
	if downloaded.Code != http.StatusOK || downloaded.Body.String() != string(content) {
		t.Fatalf("download status=%d body=%q", downloaded.Code, downloaded.Body.String())
	}
	if downloaded.Header().Get("Cache-Control") != "private, no-store" || downloaded.Header().Get("X-Content-Type-Options") != "nosniff" {
		t.Fatal("download security headers missing")
	}
}

func TestGatewayRejectsUnauthorizedConflictOffsetAndHashMismatch(t *testing.T) {
	content := []byte("hello")
	_, handler := testGateway(t)

	unauthorized := serve(handler, httptest.NewRequest(http.MethodPost, "/v1/attachments", bytes.NewReader(validUploadBody(content))))
	if unauthorized.Code != http.StatusUnauthorized {
		t.Fatalf("unauthorized status=%d", unauthorized.Code)
	}
	serve(handler, authorizedRequest(http.MethodPost, "/v1/attachments", bytes.NewReader(validUploadBody(content))))

	conflictingBody := validUploadBody([]byte("other"))
	conflict := serve(handler, authorizedRequest(http.MethodPost, "/v1/attachments", bytes.NewReader(conflictingBody)))
	if conflict.Code != http.StatusConflict {
		t.Fatalf("idempotency conflict status=%d", conflict.Code)
	}

	wrongOffset := authorizedRequest(http.MethodPatch, "/v1/attachments/upload-1", bytes.NewReader(content))
	wrongOffset.Header.Set("Upload-Offset", "2")
	offsetResponse := serve(handler, wrongOffset)
	if offsetResponse.Code != http.StatusConflict || offsetResponse.Header().Get("Upload-Offset") != "0" {
		t.Fatalf("offset status=%d offset=%q", offsetResponse.Code, offsetResponse.Header().Get("Upload-Offset"))
	}

	badBytes := []byte("HELLO")
	appendRequest := authorizedRequest(http.MethodPatch, "/v1/attachments/upload-1", bytes.NewReader(badBytes))
	appendRequest.Header.Set("Upload-Offset", "0")
	serve(handler, appendRequest)
	hashResponse := serve(handler, authorizedRequest(http.MethodPost, "/v1/attachments/upload-1/complete", nil))
	if hashResponse.Code != http.StatusUnprocessableEntity {
		t.Fatalf("hash mismatch status=%d body=%s", hashResponse.Code, hashResponse.Body.String())
	}
}

func TestExpiredReferenceIsRejected(t *testing.T) {
	s, handler := testGateway(t)
	expires := time.Now().UTC().Add(-time.Minute).Unix()
	target := "/v1/attachments/upload-1/content?expires=" +
		url.QueryEscape(strconv.FormatInt(expires, 10)) +
		"&signature=" + s.signature("upload-1", expires)
	response := serve(handler, httptest.NewRequest(http.MethodGet, target, nil))
	if response.Code != http.StatusForbidden {
		t.Fatalf("expired reference status=%d", response.Code)
	}
}
