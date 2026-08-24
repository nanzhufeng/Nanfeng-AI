// Nanfeng AI attachment gateway: an intentionally small, self-hosted resumable upload service.
// It never receives a model-provider API key and does not persist prompts or model responses.
package main

import (
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"
)

const maxRequestMetadataBytes = 8 * 1024

var safeID = regexp.MustCompile(`^[A-Za-z0-9._:-]{1,120}$`)
var sha256Hex = regexp.MustCompile(`^[0-9a-f]{64}$`)

type configuration struct {
	listen, dataDir, token, publicBase string
	maxBytes int64
	retention, referenceTTL time.Duration
}

type session struct {
	UploadID, AttemptID, AttachmentID string `json:"upload_id"`
	ProviderID, ModelID, MIMEType, ContentType string `json:"provider_id"`
	SHA256 string `json:"sha256"`
	ByteCount int64 `json:"byte_count"`
	TokenFingerprint string `json:"token_fingerprint"`
	CreatedAt, ExpiresAt time.Time `json:"created_at"`
	Completed bool `json:"completed"`
}

type createRequest struct {
	UploadID string `json:"upload_id"`
	AttemptID string `json:"attempt_id"`
	AttachmentID string `json:"attachment_id"`
	ProviderID string `json:"provider_id"`
	ModelID string `json:"model_id"`
	MIMEType string `json:"mime_type"`
	ContentType string `json:"content_type"`
	SHA256 string `json:"sha256"`
	ByteCount int64 `json:"byte_count"`
}

type server struct { cfg configuration; mu sync.Mutex }

func main() {
	cfg, err := loadConfig()
	if err != nil { log.Fatal(err) }
	if err := os.MkdirAll(cfg.dataDir, 0700); err != nil { log.Fatal(err) }
	s := &server{cfg: cfg}
	go s.cleanupLoop()
	mux := http.NewServeMux()
	mux.HandleFunc("POST /v1/attachments", s.create)
	mux.HandleFunc("HEAD /v1/attachments/{id}", s.offset)
	mux.HandleFunc("PATCH /v1/attachments/{id}", s.append)
	mux.HandleFunc("POST /v1/attachments/{id}/complete", s.complete)
	mux.HandleFunc("DELETE /v1/attachments/{id}", s.remove)
	mux.HandleFunc("GET /v1/attachments/{id}/content", s.content)
	log.Printf("attachment gateway listening on %s", cfg.listen)
	log.Fatal(http.ListenAndServe(cfg.listen, securityHeaders(mux)))
}

func loadConfig() (configuration, error) {
	require := func(name string) (string, error) { v := os.Getenv(name); if v == "" { return "", fmt.Errorf("%s is required", name) }; return v, nil }
	dataDir, err := require("NANFENG_ATTACHMENT_GATEWAY_DATA_DIR"); if err != nil { return configuration{}, err }
	token, err := require("NANFENG_ATTACHMENT_GATEWAY_TOKEN"); if err != nil { return configuration{}, err }
	base, err := require("NANFENG_ATTACHMENT_GATEWAY_PUBLIC_BASE_URL"); if err != nil { return configuration{}, err }
	u, err := url.Parse(base); if err != nil || u.Scheme != "https" || u.Host == "" || u.User != nil { return configuration{}, errors.New("NANFENG_ATTACHMENT_GATEWAY_PUBLIC_BASE_URL must be a clean https URL") }
	maxMiB := envInt("NANFENG_ATTACHMENT_GATEWAY_MAX_MIB", 150, 1, 1024)
	retentionSeconds := envInt("NANFENG_ATTACHMENT_GATEWAY_RETENTION_SECONDS", 3600, 300, 86400)
	referenceSeconds := envInt("NANFENG_ATTACHMENT_GATEWAY_REFERENCE_TTL_SECONDS", 300, 60, retentionSeconds)
	return configuration{listen: envString("NANFENG_ATTACHMENT_GATEWAY_LISTEN", ":8080"), dataDir: dataDir, token: token, publicBase: strings.TrimSuffix(base, "/"), maxBytes: int64(maxMiB) * 1024 * 1024, retention: time.Duration(retentionSeconds) * time.Second, referenceTTL: time.Duration(referenceSeconds) * time.Second}, nil
}
func envString(name, fallback string) string { if v := os.Getenv(name); v != "" { return v }; return fallback }
func envInt(name string, fallback, min, max int) int { v, err := strconv.Atoi(envString(name, strconv.Itoa(fallback))); if err != nil || v < min || v > max { return fallback }; return v }

func (s *server) create(w http.ResponseWriter, r *http.Request) {
	if !s.authorized(r) { fail(w, http.StatusUnauthorized, "UNAUTHORIZED"); return }
	var request createRequest
	r.Body = http.MaxBytesReader(w, r.Body, maxRequestMetadataBytes)
	if json.NewDecoder(r.Body).Decode(&request) != nil || !validCreate(request, s.cfg.maxBytes) { fail(w, http.StatusBadRequest, "INVALID_UPLOAD"); return }
	s.mu.Lock(); defer s.mu.Unlock()
	if existing, err := s.load(request.UploadID); err == nil {
		if existing.AttemptID != request.AttemptID || existing.AttachmentID != request.AttachmentID || existing.SHA256 != request.SHA256 || existing.ByteCount != request.ByteCount || existing.TokenFingerprint != tokenFingerprint(s.cfg.token) { fail(w, http.StatusConflict, "IDEMPOTENCY_CONFLICT"); return }
		s.respondSession(w, existing); return
	}
	now := time.Now().UTC()
	created := session{UploadID: request.UploadID, AttemptID: request.AttemptID, AttachmentID: request.AttachmentID, ProviderID: request.ProviderID, ModelID: request.ModelID, MIMEType: request.MIMEType, ContentType: request.ContentType, SHA256: request.SHA256, ByteCount: request.ByteCount, TokenFingerprint: tokenFingerprint(s.cfg.token), CreatedAt: now, ExpiresAt: now.Add(s.cfg.retention)}
	if err := s.save(created); err != nil { fail(w, http.StatusInternalServerError, "STORAGE_UNAVAILABLE"); return }
	s.respondSession(w, created)
}

func (s *server) offset(w http.ResponseWriter, r *http.Request) {
	if !s.authorized(r) { fail(w, http.StatusUnauthorized, "UNAUTHORIZED"); return }
	s.mu.Lock(); defer s.mu.Unlock()
	item, err := s.owned(r.PathValue("id")); if err != nil { fail(w, statusFor(err), "UPLOAD_NOT_FOUND"); return }
	w.Header().Set("Upload-Offset", strconv.FormatInt(s.size(item.UploadID), 10)); w.WriteHeader(http.StatusNoContent)
}

func (s *server) append(w http.ResponseWriter, r *http.Request) {
	if !s.authorized(r) { fail(w, http.StatusUnauthorized, "UNAUTHORIZED"); return }
	offset, err := strconv.ParseInt(r.Header.Get("Upload-Offset"), 10, 64); if err != nil || offset < 0 || r.ContentLength < 0 { fail(w, http.StatusBadRequest, "INVALID_OFFSET"); return }
	s.mu.Lock(); defer s.mu.Unlock()
	item, err := s.owned(r.PathValue("id")); if err != nil { fail(w, statusFor(err), "UPLOAD_NOT_FOUND"); return }
	if item.Completed { fail(w, http.StatusConflict, "UPLOAD_COMPLETED"); return }
	current := s.size(item.UploadID)
	if offset != current { w.Header().Set("Upload-Offset", strconv.FormatInt(current, 10)); fail(w, http.StatusConflict, "OFFSET_MISMATCH"); return }
	if r.ContentLength > item.ByteCount-current { fail(w, http.StatusRequestEntityTooLarge, "SIZE_DENIED"); return }
	f, err := os.OpenFile(s.bytesPath(item.UploadID), os.O_CREATE|os.O_WRONLY, 0600); if err != nil { fail(w, 500, "STORAGE_UNAVAILABLE"); return }
	_, seekErr := f.Seek(current, io.SeekStart)
	written, copyErr := io.Copy(f, io.LimitReader(r.Body, r.ContentLength))
	syncErr := f.Sync(); closeErr := f.Close()
	if seekErr != nil || copyErr != nil || syncErr != nil || closeErr != nil || written != r.ContentLength { fail(w, 500, "UPLOAD_INTERRUPTED"); return }
	updated := current + written; w.Header().Set("Upload-Offset", strconv.FormatInt(updated, 10)); w.WriteHeader(http.StatusNoContent)
}

func (s *server) complete(w http.ResponseWriter, r *http.Request) {
	if !s.authorized(r) { fail(w, http.StatusUnauthorized, "UNAUTHORIZED"); return }
	s.mu.Lock(); defer s.mu.Unlock()
	item, err := s.owned(r.PathValue("id")); if err != nil { fail(w, statusFor(err), "UPLOAD_NOT_FOUND"); return }
	if s.size(item.UploadID) != item.ByteCount { fail(w, http.StatusConflict, "OFFSET_MISMATCH"); return }
	if !item.Completed {
		sum, err := fileSHA256(s.bytesPath(item.UploadID)); if err != nil || sum != item.SHA256 { fail(w, http.StatusUnprocessableEntity, "HASH_MISMATCH"); return }
		item.Completed = true
		if err := s.save(item); err != nil { fail(w, 500, "STORAGE_UNAVAILABLE"); return }
	}
	expires := time.Now().UTC().Add(s.cfg.referenceTTL)
	reference := s.signedReference(item.UploadID, expires)
	respondJSON(w, http.StatusOK, map[string]any{"session_id": item.UploadID, "url": reference, "expires_at": expires.Format(time.RFC3339)})
}

func (s *server) remove(w http.ResponseWriter, r *http.Request) {
	if !s.authorized(r) { fail(w, http.StatusUnauthorized, "UNAUTHORIZED"); return }
	s.mu.Lock(); defer s.mu.Unlock()
	item, err := s.owned(r.PathValue("id")); if err != nil { fail(w, statusFor(err), "UPLOAD_NOT_FOUND"); return }
	s.delete(item.UploadID); w.WriteHeader(http.StatusNoContent)
}

func (s *server) content(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id"); expires, err := strconv.ParseInt(r.URL.Query().Get("expires"), 10, 64)
	if err != nil || time.Now().UTC().After(time.Unix(expires, 0)) || !hmac.Equal([]byte(r.URL.Query().Get("signature")), []byte(s.signature(id, expires))) { fail(w, http.StatusForbidden, "REFERENCE_EXPIRED"); return }
	s.mu.Lock(); item, err := s.load(id); s.mu.Unlock()
	if err != nil || !item.Completed { fail(w, http.StatusNotFound, "UPLOAD_NOT_FOUND"); return }
	f, err := os.Open(s.bytesPath(id)); if err != nil { fail(w, http.StatusNotFound, "UPLOAD_NOT_FOUND"); return }; defer f.Close()
	w.Header().Set("Content-Type", item.MIMEType); w.Header().Set("Cache-Control", "private, no-store"); w.Header().Set("X-Content-Type-Options", "nosniff")
	http.ServeContent(w, r, "attachment", item.CreatedAt, f)
}

func (s *server) respondSession(w http.ResponseWriter, item session) { respondJSON(w, http.StatusCreated, map[string]any{"session_id": item.UploadID, "offset": s.size(item.UploadID)}) }
func (s *server) authorized(r *http.Request) bool { return subtle.ConstantTimeCompare([]byte(strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")), []byte(s.cfg.token)) == 1 }
func (s *server) owned(id string) (session, error) { item, err := s.load(id); if err != nil || item.TokenFingerprint != tokenFingerprint(s.cfg.token) { return session{}, os.ErrNotExist }; return item, nil }
func (s *server) metadataPath(id string) string { return filepath.Join(s.cfg.dataDir, id+".json") }
func (s *server) bytesPath(id string) string { return filepath.Join(s.cfg.dataDir, id+".bin") }
func (s *server) load(id string) (session, error) { if !safeID.MatchString(id) { return session{}, os.ErrNotExist }; raw, err := os.ReadFile(s.metadataPath(id)); if err != nil { return session{}, err }; var item session; if json.Unmarshal(raw, &item) != nil || item.UploadID != id { return session{}, errors.New("invalid metadata") }; return item, nil }
func (s *server) save(item session) error { raw, err := json.Marshal(item); if err != nil { return err }; temp := s.metadataPath(item.UploadID)+".tmp"; if err := os.WriteFile(temp, raw, 0600); err != nil { return err }; return os.Rename(temp, s.metadataPath(item.UploadID)) }
func (s *server) delete(id string) { _ = os.Remove(s.metadataPath(id)); _ = os.Remove(s.bytesPath(id)) }
func (s *server) size(id string) int64 { info, err := os.Stat(s.bytesPath(id)); if err != nil { return 0 }; return info.Size() }
func (s *server) signature(id string, expires int64) string { mac := hmac.New(sha256.New, []byte(s.cfg.token)); _, _ = io.WriteString(mac, id+"|"+strconv.FormatInt(expires, 10)); return hex.EncodeToString(mac.Sum(nil)) }
func (s *server) signedReference(id string, expires time.Time) string { return s.cfg.publicBase+"/v1/attachments/"+url.PathEscape(id)+"/content?expires="+strconv.FormatInt(expires.Unix(), 10)+"&signature="+s.signature(id, expires.Unix()) }
func (s *server) cleanupLoop() { ticker := time.NewTicker(time.Minute); defer ticker.Stop(); for range ticker.C { s.mu.Lock(); entries, _ := os.ReadDir(s.cfg.dataDir); for _, entry := range entries { if !strings.HasSuffix(entry.Name(), ".json") { continue }; id := strings.TrimSuffix(entry.Name(), ".json"); if item, err := s.load(id); err == nil && time.Now().UTC().After(item.ExpiresAt) { s.delete(id) } }; s.mu.Unlock() } }
func validCreate(r createRequest, maxBytes int64) bool { return safeID.MatchString(r.UploadID) && safeID.MatchString(r.AttemptID) && safeID.MatchString(r.AttachmentID) && safeID.MatchString(r.ProviderID) && safeID.MatchString(r.ModelID) && r.MIMEType != "" && len(r.MIMEType) <= 160 && (r.ContentType == "IMAGE" || r.ContentType == "VIDEO" || r.ContentType == "DOCUMENT") && sha256Hex.MatchString(r.SHA256) && r.ByteCount > 0 && r.ByteCount <= maxBytes }
func tokenFingerprint(token string) string { sum := sha256.Sum256([]byte(token)); return hex.EncodeToString(sum[:]) }
func fileSHA256(path string) (string, error) { f, err := os.Open(path); if err != nil { return "", err }; defer f.Close(); sum := sha256.New(); _, err = io.Copy(sum, f); return hex.EncodeToString(sum.Sum(nil)), err }
func statusFor(err error) int { if errors.Is(err, os.ErrNotExist) { return http.StatusNotFound }; return http.StatusInternalServerError }
func respondJSON(w http.ResponseWriter, status int, body any) { w.Header().Set("Content-Type", "application/json"); w.Header().Set("Cache-Control", "no-store"); w.WriteHeader(status); _ = json.NewEncoder(w).Encode(body) }
func fail(w http.ResponseWriter, status int, code string) { respondJSON(w, status, map[string]string{"error": code}) }
func securityHeaders(next http.Handler) http.Handler { return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.Header().Set("X-Content-Type-Options", "nosniff"); w.Header().Set("Referrer-Policy", "no-referrer"); next.ServeHTTP(w, r) }) }
func randomToken() string { b := make([]byte, 32); _, _ = rand.Read(b); return hex.EncodeToString(b) }
