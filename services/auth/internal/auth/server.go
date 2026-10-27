package auth

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"

	"github.com/go-webauthn/webauthn/webauthn"
	"github.com/google/uuid"
	"github.com/redis/go-redis/v9"
)

const prefix = "/api/auth"

type Server struct {
	Config   Config
	Store    *Store
	Cache    *redis.Client
	Signer   *Signer
	Google   *GoogleProvider
	Passkeys *webauthn.WebAuthn
}
type oauthPending struct{ Verifier, Nonce, CookieHash, Redirect, AppRedirect, AppState, AppChallenge string }
type nativeGrant struct{ UserID, Redirect, Challenge string }
type tokenResponse struct {
	Access  string `json:"access_token"`
	Refresh string `json:"refresh_token,omitempty"`
	Type    string `json:"token_type"`
	Expires int    `json:"expires_in"`
	CSRF    string `json:"csrf_token,omitempty"`
	UserID  string `json:"user_id"`
}

func New(c Config, store *Store, cache *redis.Client, signer *Signer, google *GoogleProvider) (*Server, error) {
	wa, err := webauthn.New(&webauthn.Config{RPDisplayName: "Plotus", RPID: c.RPID, RPOrigins: c.RPOrigins})
	if err != nil {
		return nil, err
	}
	return &Server{Config: c, Store: store, Cache: cache, Signer: signer, Google: google, Passkeys: wa}, nil
}

func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", s.health)
	mux.HandleFunc("GET /.well-known/jwks.json", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Cache-Control", "public, max-age=300")
		writeJSON(w, 200, s.Signer.JWKS())
	})
	mux.HandleFunc("GET "+prefix+"/config", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, 200, map[string]any{"google_enabled": s.Google != nil, "passkeys_enabled": true, "rp_id": s.Config.RPID})
	})
	mux.HandleFunc("POST "+prefix+"/login/google", s.googleStart)
	mux.HandleFunc("GET "+prefix+"/native/google/start", s.nativeGoogleStart)
	mux.HandleFunc("GET "+prefix+"/login/google/callback", s.googleCallback)
	mux.HandleFunc("POST "+prefix+"/native/token/exchange", s.nativeExchange)
	mux.HandleFunc("GET "+prefix+"/token/csrf", s.csrf)
	mux.HandleFunc("GET "+prefix+"/token/access", s.webAccess)
	mux.HandleFunc("POST "+prefix+"/token/refresh", s.webRefresh)
	mux.HandleFunc("GET "+prefix+"/token/refresh/revoked", s.webSession)
	mux.HandleFunc("DELETE "+prefix+"/login/user/logout", s.webLogout)
	mux.HandleFunc("POST "+prefix+"/native/token/refresh", s.nativeRefresh)
	mux.HandleFunc("DELETE "+prefix+"/native/session", s.nativeLogout)
	mux.HandleFunc("GET "+prefix+"/user/me", s.me)
	mux.HandleFunc("POST "+prefix+"/passkey/login/begin", s.passkeyBeginLogin)
	mux.HandleFunc("POST "+prefix+"/native/passkey/login/begin", s.passkeyBeginLogin)
	mux.HandleFunc("POST "+prefix+"/passkey/login/finish", s.passkeyFinishLogin)
	mux.HandleFunc("POST "+prefix+"/native/passkey/login/finish", s.passkeyFinishLogin)
	mux.HandleFunc("POST "+prefix+"/passkey/register/begin", s.passkeyBeginRegister)
	mux.HandleFunc("POST "+prefix+"/passkey/register/finish", s.passkeyFinishRegister)
	mux.HandleFunc("GET "+prefix+"/passkey/credentials", s.passkeyList)
	mux.HandleFunc("DELETE "+prefix+"/passkey/credentials/{id}", s.passkeyDelete)
	return s.middleware(mux)
}

var rateScript = redis.NewScript(`local n=redis.call('INCR',KEYS[1]);if n==1 then redis.call('EXPIRE',KEYS[1],60) end;return n`)

func (s *Server) middleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Cache-Control", "no-store")
		w.Header().Set("X-Content-Type-Options", "nosniff")
		origin := r.Header.Get("Origin")
		if origin != "" {
			if origin != s.Config.WebOrigin && origin != s.Config.PublicURL {
				fail(w, 403, "ORIGIN_DENIED", "Origin is not allowed.")
				return
			}
			w.Header().Set("Access-Control-Allow-Origin", origin)
			w.Header().Set("Vary", "Origin")
			w.Header().Set("Access-Control-Allow-Credentials", "true")
		}
		if r.Method == "OPTIONS" {
			w.Header().Set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
			w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-CSRF-Token")
			w.WriteHeader(204)
			return
		}
		ctx, cancel := context.WithTimeout(r.Context(), 20*time.Second)
		defer cancel()
		r = r.WithContext(ctx)
		r.Body = http.MaxBytesReader(w, r.Body, 1<<20)
		if r.URL.Path != "/health" && r.URL.Path != "/.well-known/jwks.json" {
			ip, _, _ := net.SplitHostPort(r.RemoteAddr)
			// Forwarded headers are deliberately not trusted without a proxy policy.
			count, err := rateScript.Run(ctx, s.Cache, []string{"plotus:rate:" + digest(ip)}).Int()
			if err != nil {
				fail(w, 503, "AUTH_UNAVAILABLE", "Authentication is temporarily unavailable.")
				return
			}
			if count > 120 {
				w.Header().Set("Retry-After", "60")
				fail(w, 429, "RATE_LIMITED", "Try again shortly.")
				return
			}
		}
		next.ServeHTTP(w, r)
	})
}

func (s *Server) health(w http.ResponseWriter, r *http.Request) {
	var schema bool
	err := s.Store.Pool.QueryRow(r.Context(), "SELECT to_regclass('plotus_auth.sessions') IS NOT NULL").Scan(&schema)
	if err != nil || !schema || s.Cache.Ping(r.Context()).Err() != nil {
		fail(w, 503, "NOT_READY", "Authentication storage is not ready.")
		return
	}
	writeJSON(w, 200, map[string]string{"status": "ok", "service": "plotus-auth"})
}
func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}
func fail(w http.ResponseWriter, status int, code, message string) {
	writeJSON(w, status, map[string]any{"error": map[string]string{"code": code, "message": message}})
}
func decode(r *http.Request, dst any) error {
	d := json.NewDecoder(r.Body)
	d.DisallowUnknownFields()
	if err := d.Decode(dst); err != nil {
		return err
	}
	if err := d.Decode(&struct{}{}); !errors.Is(err, io.EOF) {
		return errors.New("invalid JSON payload")
	}
	return nil
}
func (s *Server) cookieName(name string) string {
	if s.Config.Production {
		return "__Host-plotus_" + name
	}
	return "plotus_" + name
}
func (s *Server) setCookie(w http.ResponseWriter, name, value string, ttl time.Duration, httpOnly bool) {
	http.SetCookie(w, &http.Cookie{Name: s.cookieName(name), Value: value, Path: "/", HttpOnly: httpOnly, Secure: s.Config.Production || strings.HasPrefix(s.Config.PublicURL, "https://"), SameSite: http.SameSiteLaxMode, MaxAge: int(ttl.Seconds()), Expires: time.Now().Add(ttl)})
}
func (s *Server) clearCookie(w http.ResponseWriter, name string) {
	s.setCookie(w, name, "", -time.Hour, true)
}
func (s *Server) cookie(r *http.Request, name string) string {
	c, err := r.Cookie(s.cookieName(name))
	if err != nil {
		return ""
	}
	return c.Value
}
func (s *Server) originOK(r *http.Request) bool {
	return r.Header.Get("Origin") == s.Config.WebOrigin || r.Header.Get("Origin") == s.Config.PublicURL ||
		(r.Header.Get("Origin") == "" && r.Header.Get("Sec-Fetch-Site") == "same-origin")
}
func safeRelative(raw string) bool {
	u, err := url.Parse(raw)
	return err == nil && strings.HasPrefix(raw, "/") && !strings.HasPrefix(raw, "//") && !strings.ContainsAny(raw, "\\\r\n") && u.Host == "" && !u.IsAbs()
}
func (s *Server) put(ctx context.Context, key string, value any, ttl time.Duration) error {
	data, err := json.Marshal(value)
	if err != nil {
		return err
	}
	return s.Cache.Set(ctx, key, data, ttl).Err()
}

func (s *Server) startOAuth(w http.ResponseWriter, r *http.Request, p oauthPending) (string, error) {
	state, cookie := randomToken(), randomToken()
	p.Verifier = randomToken()
	p.Nonce = randomToken()
	p.CookieHash = digest(cookie)
	if err := s.put(r.Context(), "plotus:oauth:"+digest(state), p, 10*time.Minute); err != nil {
		return "", err
	}
	s.setCookie(w, "oauth", cookie, 10*time.Minute, true)
	return s.Google.URL(state, p.Verifier, p.Nonce), nil
}
func (s *Server) googleStart(w http.ResponseWriter, r *http.Request) {
	if !s.originOK(r) {
		fail(w, 403, "ORIGIN_DENIED", "Origin is not allowed.")
		return
	}
	if s.Google == nil {
		fail(w, 503, "GOOGLE_NOT_CONFIGURED", "Google sign-in is not configured.")
		return
	}
	var body struct {
		Redirect string `json:"redirect"`
	}
	if err := decode(r, &body); err != nil {
		fail(w, 400, "INVALID_REQUEST", "Invalid request.")
		return
	}
	if body.Redirect == "" {
		body.Redirect = "/"
	}
	if !safeRelative(body.Redirect) {
		fail(w, 400, "INVALID_REDIRECT", "Use a relative app path.")
		return
	}
	target, err := s.startOAuth(w, r, oauthPending{Redirect: body.Redirect})
	if err != nil {
		fail(w, 503, "AUTH_UNAVAILABLE", "Authentication is temporarily unavailable.")
		return
	}
	writeJSON(w, 200, map[string]string{"authUrl": target})
}

var urlSafe = regexp.MustCompile(`^[A-Za-z0-9_-]+$`)

func (s *Server) nativeGoogleStart(w http.ResponseWriter, r *http.Request) {
	if s.Google == nil {
		fail(w, 503, "GOOGLE_NOT_CONFIGURED", "Google sign-in is not configured.")
		return
	}
	q := r.URL.Query()
	redirect, state, challenge := q.Get("redirect_uri"), q.Get("state"), q.Get("code_challenge")
	if !s.Config.AllowNativeRedirect(redirect) || len(state) < 20 || len(state) > 512 || !urlSafe.MatchString(state) || len(challenge) != 43 || !urlSafe.MatchString(challenge) || q.Get("code_challenge_method") != "S256" {
		fail(w, 400, "INVALID_REQUEST", "Invalid native authorization request.")
		return
	}
	target, err := s.startOAuth(w, r, oauthPending{AppRedirect: redirect, AppState: state, AppChallenge: challenge})
	if err != nil {
		fail(w, 503, "AUTH_UNAVAILABLE", "Authentication is temporarily unavailable.")
		return
	}
	http.Redirect(w, r, target, http.StatusFound)
}
func (s *Server) oauthFailure(w http.ResponseWriter, r *http.Request, p oauthPending) {
	if p.AppRedirect != "" {
		u, _ := url.Parse(p.AppRedirect)
		q := u.Query()
		q.Set("state", p.AppState)
		q.Set("error", "oauth_failed")
		u.RawQuery = q.Encode()
		http.Redirect(w, r, u.String(), 302)
		return
	}
	http.Redirect(w, r, s.Config.WebOrigin+"/?auth_error=oauth_failed", 302)
}
func (s *Server) googleCallback(w http.ResponseWriter, r *http.Request) {
	raw, err := s.Cache.GetDel(r.Context(), "plotus:oauth:"+digest(r.URL.Query().Get("state"))).Bytes()
	if err != nil && !errors.Is(err, redis.Nil) {
		authError(w, err)
		return
	}
	var pending oauthPending
	if err != nil || json.Unmarshal(raw, &pending) != nil || !equal(pending.CookieHash, digest(s.cookie(r, "oauth"))) {
		fail(w, 400, "INVALID_OAUTH_STATE", "Sign-in expired. Start again.")
		return
	}
	s.clearCookie(w, "oauth")
	if s.Google == nil || r.URL.Query().Get("error") != "" {
		s.oauthFailure(w, r, pending)
		return
	}
	identity, err := s.Google.Exchange(r.Context(), r.URL.Query().Get("code"), pending.Verifier, pending.Nonce)
	if err != nil {
		s.oauthFailure(w, r, pending)
		return
	}
	user, err := s.Store.GoogleUser(r.Context(), identity.Subject, identity.Email, identity.Name, identity.Picture)
	if err != nil {
		fail(w, 503, "AUTH_UNAVAILABLE", "Authentication is temporarily unavailable.")
		return
	}
	if pending.AppRedirect != "" {
		code := randomToken()
		grant := nativeGrant{user.ID, pending.AppRedirect, pending.AppChallenge}
		if s.put(r.Context(), "plotus:grant:"+digest(code), grant, 90*time.Second) != nil {
			fail(w, 503, "AUTH_UNAVAILABLE", "Authentication is temporarily unavailable.")
			return
		}
		target, _ := url.Parse(pending.AppRedirect)
		q := target.Query()
		q.Set("code", code)
		q.Set("state", pending.AppState)
		target.RawQuery = q.Encode()
		http.Redirect(w, r, target.String(), 302)
		return
	}
	if _, err = s.issue(w, r, user.ID, "web"); err != nil {
		fail(w, 503, "AUTH_UNAVAILABLE", "Authentication is temporarily unavailable.")
		return
	}
	http.Redirect(w, r, s.Config.WebOrigin+pending.Redirect, 302)
}
func (s *Server) nativeExchange(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Code     string `json:"code"`
		Verifier string `json:"code_verifier"`
		Redirect string `json:"redirect_uri"`
	}
	if decode(r, &body) != nil || len(body.Verifier) < 43 || len(body.Verifier) > 128 || body.Code == "" {
		fail(w, 400, "INVALID_GRANT", "Invalid authorization grant.")
		return
	}
	key := "plotus:grant:" + digest(body.Code)
	raw, err := s.Cache.Get(r.Context(), key).Bytes()
	if err != nil && !errors.Is(err, redis.Nil) {
		authError(w, err)
		return
	}
	var grant nativeGrant
	if err != nil || json.Unmarshal(raw, &grant) != nil || !equal(grant.Challenge, pkce(body.Verifier)) || grant.Redirect != body.Redirect {
		fail(w, 401, "INVALID_GRANT", "Authorization grant is invalid or expired.")
		return
	}
	consumed, err := s.Cache.GetDel(r.Context(), key).Bytes()
	if err != nil && !errors.Is(err, redis.Nil) {
		authError(w, err)
		return
	}
	if err != nil || !equal(string(consumed), string(raw)) {
		fail(w, 401, "INVALID_GRANT", "Authorization grant has already been used.")
		return
	}
	tokens, err := s.issue(w, r, grant.UserID, "native")
	if err != nil {
		fail(w, 503, "AUTH_UNAVAILABLE", "Authentication is temporarily unavailable.")
		return
	}
	writeJSON(w, 200, tokens)
}
func (s *Server) issue(w http.ResponseWriter, r *http.Request, userID, kind string) (tokenResponse, error) {
	refresh, csrf := randomToken(), randomToken()
	session := Session{uuid.NewString(), userID, kind, digest(refresh), csrf, time.Now().Add(7 * 24 * time.Hour)}
	access, err := s.Signer.Access(userID, session.ID)
	if err != nil {
		return tokenResponse{}, err
	}
	if err = s.Store.CreateSession(r.Context(), session); err != nil {
		return tokenResponse{}, err
	}
	if kind == "web" {
		s.setCookie(w, "refresh", refresh, 7*24*time.Hour, true)
		s.setCookie(w, "csrf", csrf, 7*24*time.Hour, false)
		refresh = ""
	}
	return tokenResponse{access, refresh, "Bearer", 900, csrf, userID}, nil
}
