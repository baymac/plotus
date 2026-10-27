package auth

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/redis/go-redis/v9"
)

func integrationServer(t *testing.T) (*Server, User) {
	t.Helper()
	database := os.Getenv("PLOTUS_AUTH_TEST_DATABASE_URL")
	cacheURL := os.Getenv("PLOTUS_AUTH_TEST_REDIS_URL")
	if database == "" || cacheURL == "" {
		t.Skip("set isolated Plotus auth test storage; see scripts/check-integration.sh")
	}
	ctx := context.Background()
	pool, err := pgxpool.New(ctx, database)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(pool.Close)
	options, err := redis.ParseURL(cacheURL)
	if err != nil {
		t.Fatal(err)
	}
	cache := redis.NewClient(options)
	t.Cleanup(func() { _ = cache.Close() })
	store := &Store{Pool: pool}
	if err = store.Migrate(ctx); err != nil {
		t.Fatal(err)
	}
	user, err := store.GoogleUser(ctx, "isolated-test-"+randomToken(), "fixture@example.invalid", "Test fixture", "")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		_, _ = pool.Exec(ctx, "DELETE FROM plotus_auth.passkeys WHERE user_id=$1", user.ID)
		_, _ = pool.Exec(ctx, "DELETE FROM plotus_auth.sessions WHERE user_id=$1", user.ID)
		_, _ = pool.Exec(ctx, "DELETE FROM plotus_auth.users WHERE id=$1", user.ID)
	})
	config := Config{PublicURL: "http://localhost:4001", WebOrigin: "http://localhost:8081", RPID: "localhost", RPOrigins: []string{"http://localhost:8081"}, NativeRedirects: []string{"plotus://oauth/callback"}}
	server, err := New(config, store, cache, testSigner(t), nil)
	if err != nil {
		t.Fatal(err)
	}
	return server, user
}
func request(h http.Handler, method, path string, body any, headers map[string]string) *httptest.ResponseRecorder {
	var data []byte
	if body != nil {
		data, _ = json.Marshal(body)
	}
	r := httptest.NewRequest(method, path, bytes.NewReader(data))
	r.Header.Set("Content-Type", "application/json")
	for key, value := range headers {
		r.Header.Set(key, value)
	}
	w := httptest.NewRecorder()
	h.ServeHTTP(w, r)
	return w
}
func TestNativeProofReplayRotationAndRevocation(t *testing.T) {
	s, u := integrationServer(t)
	h := s.Handler()
	ctx := context.Background()
	code, verifier := randomToken(), randomToken()
	redirect := "plotus://oauth/callback"
	if err := s.put(ctx, "plotus:grant:"+digest(code), nativeGrant{u.ID, redirect, pkce(verifier)}, time.Minute); err != nil {
		t.Fatal(err)
	}
	exchange := func(proof string) *httptest.ResponseRecorder {
		return request(h, "POST", prefix+"/native/token/exchange", map[string]string{"code": code, "code_verifier": proof, "redirect_uri": redirect}, nil)
	}
	if w := exchange(randomToken()); w.Code != 401 {
		t.Fatal("bad proof", w.Code, w.Body)
	}
	w := exchange(verifier)
	if w.Code != 200 {
		t.Fatal("valid proof", w.Code, w.Body)
	}
	var tokens tokenResponse
	if err := json.Unmarshal(w.Body.Bytes(), &tokens); err != nil {
		t.Fatal(err)
	}
	if w = exchange(verifier); w.Code != 401 {
		t.Fatal("replayed grant", w.Code)
	}
	var stored string
	if err := s.Store.Pool.QueryRow(ctx, "SELECT refresh_hash FROM plotus_auth.sessions WHERE user_id=$1", u.ID).Scan(&stored); err != nil {
		t.Fatal(err)
	}
	if stored == tokens.Refresh || stored != digest(tokens.Refresh) {
		t.Fatal("refresh credential is not hashed")
	}
	refresh := func(csrf string) *httptest.ResponseRecorder {
		return request(h, "POST", prefix+"/native/token/refresh", map[string]string{"refresh_token": tokens.Refresh, "csrf_token": csrf}, nil)
	}
	if w = refresh("wrong"); w.Code != 401 {
		t.Fatal("bad CSRF", w.Code)
	}
	w = refresh(tokens.CSRF)
	if w.Code != 200 {
		t.Fatal("refresh", w.Code, w.Body)
	}
	var rotated tokenResponse
	_ = json.Unmarshal(w.Body.Bytes(), &rotated)
	if rotated.Refresh == tokens.Refresh || rotated.Refresh == "" {
		t.Fatal("refresh was not rotated")
	}
	if w = refresh(tokens.CSRF); w.Code != 401 {
		t.Fatal("old refresh replay", w.Code)
	}
	bearer := map[string]string{"Authorization": "Bearer " + rotated.Access}
	if w = request(h, "GET", prefix+"/user/me", nil, bearer); w.Code != 200 {
		t.Fatal("me", w.Code, w.Body)
	}
	if w = request(h, "DELETE", prefix+"/native/session", map[string]string{"refresh_token": rotated.Refresh, "csrf_token": rotated.CSRF}, nil); w.Code != 204 {
		t.Fatal("logout", w.Code, w.Body)
	}
	if w = request(h, "GET", prefix+"/user/me", nil, bearer); w.Code != 401 {
		t.Fatal("revoked access", w.Code)
	}
}
func TestBrowserCookiesAndOriginProtection(t *testing.T) {
	s, u := integrationServer(t)
	h := s.Handler()
	w := httptest.NewRecorder()
	r := httptest.NewRequest("GET", "/callback", nil)
	tokens, err := s.issue(w, r, u.ID, "web")
	if err != nil {
		t.Fatal(err)
	}
	if tokens.Refresh != "" {
		t.Fatal("web refresh leaked into JSON")
	}
	cookies := w.Result().Cookies()
	cookieHeader := ""
	for _, c := range cookies {
		cookieHeader += c.Name + "=" + c.Value + "; "
		if strings.Contains(c.Name, "refresh") && !c.HttpOnly {
			t.Fatal("refresh cookie is not HttpOnly")
		}
	}
	headers := map[string]string{"Origin": s.Config.WebOrigin, "Cookie": cookieHeader, "X-CSRF-Token": tokens.CSRF}
	if w = request(h, "GET", prefix+"/token/access", nil, headers); w.Code != 200 {
		t.Fatal(w.Code, w.Body)
	}
	headers["Origin"] = "https://evil.invalid"
	if w = request(h, "GET", prefix+"/token/access", nil, headers); w.Code != 403 {
		t.Fatal("cross-origin request accepted", w.Code)
	}
	headers["Origin"] = s.Config.WebOrigin
	headers["X-CSRF-Token"] = "wrong"
	if w = request(h, "POST", prefix+"/token/refresh", nil, headers); w.Code != 401 {
		t.Fatal("missing CSRF accepted", w.Code)
	}
	headers["X-CSRF-Token"] = tokens.CSRF
	if w = request(h, "DELETE", prefix+"/login/user/logout", nil, headers); w.Code != 204 {
		t.Fatal("logout", w.Code, w.Body)
	}
	if w = request(h, "GET", prefix+"/token/access", nil, headers); w.Code != 401 {
		t.Fatal("revoked cookie accepted", w.Code)
	}
}

func TestPasskeyChallengeBindingAndForgedAssertion(t *testing.T) {
	s, u := integrationServer(t)
	h := s.Handler()
	headers := map[string]string{"Origin": s.Config.WebOrigin}
	w := request(h, "POST", prefix+"/passkey/login/begin", map[string]any{}, headers)
	if w.Code != 200 {
		t.Fatal("begin", w.Code, w.Body)
	}
	var options struct {
		PublicKey struct {
			Challenge    string `json:"challenge"`
			Verification string `json:"userVerification"`
		} `json:"publicKey"`
	}
	if err := json.Unmarshal(w.Body.Bytes(), &options); err != nil || options.PublicKey.Challenge == "" || options.PublicKey.Verification != "required" {
		t.Fatal("invalid WebAuthn options", w.Body, err)
	}
	cookies := w.Result().Cookies()
	if len(cookies) != 1 || !cookies[0].HttpOnly {
		t.Fatal("missing challenge binding cookie")
	}
	if noCookie := request(h, "POST", prefix+"/passkey/login/finish", map[string]any{}, headers); noCookie.Code != 401 {
		t.Fatal("accepted unbound assertion", noCookie.Code)
	}
	headers["Cookie"] = cookies[0].Name + "=" + cookies[0].Value
	if forged := request(h, "POST", prefix+"/passkey/login/finish", map[string]any{}, headers); forged.Code != 401 {
		t.Fatal("accepted forged assertion", forged.Code, forged.Body)
	}
	if replay := request(h, "POST", prefix+"/passkey/login/finish", map[string]any{}, headers); replay.Code != 401 {
		t.Fatal("accepted replay", replay.Code)
	}
	var count int
	if err := s.Store.Pool.QueryRow(context.Background(), "SELECT count(*) FROM plotus_auth.sessions WHERE user_id=$1", u.ID).Scan(&count); err != nil || count != 0 {
		t.Fatal("invalid assertion created session", count, err)
	}
	if enroll := request(h, "POST", prefix+"/passkey/register/begin", nil, headers); enroll.Code != 401 {
		t.Fatal("unauthenticated enrollment", enroll.Code)
	}
}
