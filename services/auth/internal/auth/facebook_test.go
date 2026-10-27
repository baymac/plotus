package auth

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
	"time"
)

func facebookFixture(t *testing.T, subject, invalid string) *FacebookProvider {
	t.Helper()
	const appID, secret, token = "fixture-app", "fixture-secret", "fixture-user-token"
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		q := r.URL.Query()
		switch r.URL.Path {
		case "/oauth/access_token":
			if q.Get("client_id") != appID || q.Get("client_secret") != secret || q.Get("code") != "fixture-code" || q.Get("redirect_uri") != "https://auth.plotus.example/api/auth/login/facebook/callback" {
				t.Error("incorrect server-side token exchange")
			}
			if invalid == "exchange" {
				w.WriteHeader(400)
				return
			}
			writeJSON(w, 200, map[string]string{"access_token": token})
		case "/debug_token":
			if q.Get("input_token") != token || r.Header.Get("Authorization") != "Bearer "+appID+"|"+secret {
				t.Error("missing app authentication")
			}
			data := map[string]any{"app_id": appID, "user_id": subject, "type": "USER", "is_valid": true, "expires_at": time.Now().Add(time.Hour).Unix(), "data_access_expires_at": time.Now().Add(24 * time.Hour).Unix()}
			switch invalid {
			case "app":
				data["app_id"] = "another-app"
			case "expired":
				data["expires_at"] = time.Now().Add(-time.Hour).Unix()
			case "data-expired":
				data["data_access_expires_at"] = time.Now().Add(-time.Hour).Unix()
			case "invalid":
				data["is_valid"] = false
			case "page":
				data["type"] = "PAGE"
			}
			writeJSON(w, 200, map[string]any{"data": data})
		case "/me":
			mac := hmac.New(sha256.New, []byte(secret))
			_, _ = mac.Write([]byte(token))
			if r.Header.Get("Authorization") != "Bearer "+token || q.Get("appsecret_proof") != hex.EncodeToString(mac.Sum(nil)) || q.Get("fields") != "id,name,email,picture.type(square)" {
				t.Error("profile request is not authenticated")
			}
			id, email := subject, "fixture@example.invalid"
			if invalid == "subject" {
				id = "another-user"
			}
			if invalid == "no-email" {
				email = ""
			}
			writeJSON(w, 200, map[string]any{"id": id, "name": "Facebook fixture", "email": email, "picture": map[string]any{"data": map[string]string{"url": "https://example.invalid/avatar"}}})
		default:
			t.Error("unexpected provider endpoint")
			w.WriteHeader(404)
		}
	}))
	t.Cleanup(upstream.Close)
	return &FacebookProvider{appID: appID, secret: secret, callback: "https://auth.plotus.example/api/auth/login/facebook/callback",
		dialog: "https://www.facebook.com/v26.0/dialog/oauth", graph: upstream.URL, client: upstream.Client()}
}

func TestFacebookProviderValidation(t *testing.T) {
	for _, invalid := range []string{"", "no-email", "app", "expired", "data-expired", "invalid", "page", "subject", "exchange"} {
		t.Run(invalid, func(t *testing.T) {
			provider := facebookFixture(t, "fixture-subject", invalid)
			identity, err := provider.Exchange(context.Background(), "fixture-code")
			valid := invalid == "" || invalid == "no-email"
			if valid && (err != nil || identity.Subject != "fixture-subject" || identity.Name == "") {
				t.Fatal("valid identity rejected", err)
			}
			if !valid && err == nil {
				t.Fatal("invalid provider response accepted")
			}
			if invalid == "no-email" && identity.Email != "" {
				t.Fatal("email was fabricated")
			}
			target, _ := url.Parse(provider.URL("fixture-state"))
			q := target.Query()
			if q.Get("state") != "fixture-state" || q.Get("redirect_uri") != provider.callback || q.Get("response_type") != "code" || strings.Contains(target.String(), provider.secret) {
				t.Fatal("invalid authorization URL")
			}
		})
	}
}

func TestFacebookConfigValidation(t *testing.T) {
	base := Config{DatabaseURL: "configured", KeyFile: "configured", PublicURL: "http://localhost:4001", WebOrigin: "http://localhost:8081", FacebookID: "fixture-app", FacebookSecret: "fixture-secret", FacebookCallbackURL: "https://auth.plotus.example/api/auth/login/facebook/callback", FacebookVersion: "v26.0"}
	if err := base.Validate(); err != nil {
		t.Fatal(err)
	}
	for _, mutate := range []func(*Config){
		func(c *Config) { c.FacebookSecret = "" },
		func(c *Config) { c.FacebookCallbackURL = "http://localhost:4001/api/auth/login/facebook/callback" },
		func(c *Config) { c.FacebookCallbackURL += "?redirect=https://example.invalid" },
		func(c *Config) { c.FacebookCallbackURL = "https://example.invalid/another-callback" },
		func(c *Config) { c.FacebookVersion = "../../unexpected" },
	} {
		bad := base
		mutate(&bad)
		if bad.Validate() == nil {
			t.Fatal("invalid Facebook configuration accepted")
		}
	}
}

func TestFacebookBrowserHandoffAndProviderIsolation(t *testing.T) {
	s, google := integrationServer(t)
	s.Facebook = facebookFixture(t, "isolated-facebook-"+randomToken(), "no-email")
	h := s.Handler()
	ctx := context.Background()
	// Store the fixture subject separately for cleanup without inspecting provider tokens.
	var subject string
	upstreamIdentity, err := s.Facebook.Exchange(ctx, "fixture-code")
	if err != nil {
		t.Fatal(err)
	}
	subject = upstreamIdentity.Subject
	t.Cleanup(func() {
		_, _ = s.Store.Pool.Exec(ctx, "DELETE FROM plotus_auth.sessions WHERE user_id IN (SELECT id FROM plotus_auth.users WHERE facebook_subject=$1)", subject)
		_, _ = s.Store.Pool.Exec(ctx, "DELETE FROM plotus_auth.users WHERE facebook_subject=$1", subject)
	})
	state, verifier := randomToken(), randomToken()
	body := map[string]string{"redirect": "/", "state": state, "code_challenge": pkce(verifier), "code_challenge_method": "S256"}
	origin := map[string]string{"Origin": s.Config.WebOrigin}
	if w := request(h, "POST", prefix+"/login/facebook", body, nil); w.Code != 403 {
		t.Fatal("unbound start accepted", w.Code)
	}
	body["redirect"] = "//example.invalid"
	if w := request(h, "POST", prefix+"/login/facebook", body, origin); w.Code != 400 {
		t.Fatal("open redirect accepted", w.Code)
	}
	body["redirect"] = "/"
	begin := func() (string, string) {
		t.Helper()
		w := request(h, "POST", prefix+"/login/facebook", body, origin)
		if w.Code != 200 {
			t.Fatal("start", w.Code, w.Body)
		}
		var response map[string]string
		_ = json.Unmarshal(w.Body.Bytes(), &response)
		target := response["authUrl"]
		w = request(h, "GET", target, nil, nil)
		if w.Code != 302 {
			t.Fatal("authorize", w.Code, w.Body)
		}
		authorize, _ := url.Parse(w.Header().Get("Location"))
		var binding string
		for _, cookie := range w.Result().Cookies() {
			if !cookie.Secure || !cookie.HttpOnly || cookie.SameSite != http.SameSiteLaxMode {
				t.Fatal("insecure provider cookie")
			}
			binding = cookie.Name + "=" + cookie.Value
		}
		if w = request(h, "GET", target, nil, nil); w.Code != 400 {
			t.Fatal("authorization ticket replayed")
		}
		return authorize.Query().Get("state"), binding
	}
	callbackState, cookie := begin()
	if w := request(h, "GET", prefix+"/login/facebook/callback?state="+callbackState+"&code=fixture-code", nil, nil); w.Code != 400 {
		t.Fatal("unbound callback accepted")
	}
	callbackState, cookie = begin()
	if w := request(h, "GET", prefix+"/login/google/callback?state="+callbackState+"&code=fixture-code", nil, map[string]string{"Cookie": cookie}); w.Code != 400 {
		t.Fatal("cross-provider callback accepted")
	}
	w := request(h, "GET", prefix+"/login/facebook/callback?state="+callbackState+"&code=fixture-code", nil, map[string]string{"Cookie": cookie})
	if w.Code != 302 {
		t.Fatal("callback", w.Code, w.Body)
	}
	returned, _ := url.Parse(w.Header().Get("Location"))
	fragment, _ := url.ParseQuery(returned.Fragment)
	code := fragment.Get("auth_code")
	if returned.Scheme+"://"+returned.Host != s.Config.WebOrigin || fragment.Get("auth_state") != state || len(code) != 43 || returned.RawQuery != "" {
		t.Fatal("invalid browser handoff")
	}
	if w = request(h, "GET", prefix+"/login/facebook/callback?state="+callbackState+"&code=fixture-code", nil, map[string]string{"Cookie": cookie}); w.Code != 400 {
		t.Fatal("callback replay accepted")
	}
	exchange := func(proof string, headers map[string]string) *httptest.ResponseRecorder {
		return request(h, "POST", prefix+"/login/facebook/exchange", map[string]string{"code": code, "code_verifier": proof}, headers)
	}
	if w = exchange(verifier, nil); w.Code != 403 {
		t.Fatal("exchange without origin accepted")
	}
	if w = exchange(randomToken(), origin); w.Code != 401 {
		t.Fatal("wrong browser proof accepted")
	}
	if w = request(h, "POST", prefix+"/native/token/exchange", map[string]string{"code": code, "code_verifier": verifier, "redirect_uri": s.Config.WebOrigin}, nil); w.Code != 401 {
		t.Fatal("browser grant exchanged as native")
	}
	w = exchange(verifier, origin)
	if w.Code != 200 {
		t.Fatal("exchange", w.Code, w.Body)
	}
	var tokens tokenResponse
	_ = json.Unmarshal(w.Body.Bytes(), &tokens)
	if tokens.Refresh != "" || tokens.Access == "" || tokens.UserID == google.ID {
		t.Fatal("invalid browser session or provider collision")
	}
	for _, cookie := range w.Result().Cookies() {
		if strings.Contains(cookie.Name, "refresh") && !cookie.HttpOnly {
			t.Fatal("refresh exposed")
		}
	}
	if w = exchange(verifier, origin); w.Code != 401 {
		t.Fatal("browser grant replay accepted")
	}
	profile, err := s.Store.User(ctx, tokens.UserID)
	if err != nil || profile.Email != "" {
		t.Fatal("optional email was fabricated")
	}
	w = request(h, "POST", prefix+"/passkey/register/begin", map[string]string{}, map[string]string{"Origin": s.Config.WebOrigin, "Authorization": "Bearer " + tokens.Access})
	if w.Code != 200 {
		t.Fatal("Facebook account without email cannot enroll a passkey", w.Code, w.Body)
	}
	var enrollment struct {
		PublicKey struct {
			User struct {
				Name string `json:"name"`
			} `json:"user"`
		} `json:"publicKey"`
	}
	if json.Unmarshal(w.Body.Bytes(), &enrollment) != nil || enrollment.PublicKey.User.Name != profile.Name {
		t.Fatal("passkey account name is missing")
	}
	// The same provider subject and email must never overwrite a Google identity.
	isolated, err := s.Store.FacebookUser(ctx, googleSubject(t, s, google.ID), google.Email, "Separate Facebook identity", "")
	if err != nil || isolated.ID == google.ID {
		t.Fatal("provider identity collision", err)
	}
	t.Cleanup(func() { _, _ = s.Store.Pool.Exec(ctx, "DELETE FROM plotus_auth.users WHERE id=$1", isolated.ID) })
}

func googleSubject(t *testing.T, s *Server, id string) string {
	t.Helper()
	var subject string
	if err := s.Store.Pool.QueryRow(context.Background(), "SELECT google_subject FROM plotus_auth.users WHERE id=$1", id).Scan(&subject); err != nil {
		t.Fatal(err)
	}
	return subject
}

func TestFacebookNativeBrowserProofAndCancellation(t *testing.T) {
	s, _ := integrationServer(t)
	subject := "isolated-native-facebook-" + randomToken()
	s.Facebook = facebookFixture(t, subject, "")
	h := s.Handler()
	ctx := context.Background()
	t.Cleanup(func() {
		_, _ = s.Store.Pool.Exec(ctx, "DELETE FROM plotus_auth.sessions WHERE user_id IN (SELECT id FROM plotus_auth.users WHERE facebook_subject=$1)", subject)
		_, _ = s.Store.Pool.Exec(ctx, "DELETE FROM plotus_auth.users WHERE facebook_subject=$1", subject)
	})
	verifier, state := randomToken(), randomToken()
	body := map[string]string{"redirect_uri": "plotus://oauth/callback", "state": state, "code_challenge": pkce(verifier), "code_challenge_method": "S256"}
	body["redirect_uri"] = "otherapp://oauth/callback"
	if w := request(h, "POST", prefix+"/native/facebook", body, nil); w.Code != 400 {
		t.Fatal("unregistered app redirect accepted")
	}
	body["redirect_uri"] = "plotus://oauth/callback"
	begin := func() (string, string) {
		t.Helper()
		w := request(h, "POST", prefix+"/native/facebook", body, nil)
		if w.Code != 200 {
			t.Fatal("native start", w.Code, w.Body)
		}
		var result map[string]string
		_ = json.Unmarshal(w.Body.Bytes(), &result)
		w = request(h, "GET", result["authUrl"], nil, nil)
		if w.Code != 302 {
			t.Fatal("native authorize", w.Code)
		}
		target, _ := url.Parse(w.Header().Get("Location"))
		cookie := w.Result().Cookies()[0]
		return target.Query().Get("state"), cookie.Name + "=" + cookie.Value
	}
	callbackState, cookie := begin()
	w := request(h, "GET", prefix+"/login/facebook/callback?state="+callbackState+"&error=access_denied", nil, map[string]string{"Cookie": cookie})
	returned, _ := url.Parse(w.Header().Get("Location"))
	if w.Code != 302 || returned.Query().Get("state") != state || returned.Query().Get("error") == "" || returned.Query().Get("code") != "" {
		t.Fatal("cancellation lost state or issued a grant")
	}
	callbackState, cookie = begin()
	w = request(h, "GET", prefix+"/login/facebook/callback?state="+callbackState+"&code=fixture-code", nil, map[string]string{"Cookie": cookie})
	returned, _ = url.Parse(w.Header().Get("Location"))
	code := returned.Query().Get("code")
	if w.Code != 302 || returned.Scheme != "plotus" || returned.Host != "oauth" || returned.Path != "/callback" || returned.Query().Get("state") != state || len(code) != 43 || strings.Contains(returned.String(), "access_token") {
		t.Fatal("invalid native handoff")
	}
	exchange := func(proof, redirect string) *httptest.ResponseRecorder {
		return request(h, "POST", prefix+"/native/token/exchange", map[string]string{"code": code, "code_verifier": proof, "redirect_uri": redirect}, nil)
	}
	if w = exchange(randomToken(), body["redirect_uri"]); w.Code != 401 {
		t.Fatal("invalid proof accepted")
	}
	if w = exchange(verifier, "otherapp://oauth/callback"); w.Code != 401 {
		t.Fatal("incorrect redirect accepted")
	}
	w = exchange(verifier, body["redirect_uri"])
	if w.Code != 200 {
		t.Fatal("native exchange", w.Code, w.Body)
	}
	var tokens tokenResponse
	_ = json.Unmarshal(w.Body.Bytes(), &tokens)
	if len(tokens.Refresh) != 43 || tokens.Access == "" {
		t.Fatal("native session missing")
	}
	if w = request(h, "GET", prefix+"/user/me", nil, map[string]string{"Authorization": "Bearer " + tokens.Access}); w.Code != 200 {
		t.Fatal("native session unusable")
	}
	if w = exchange(verifier, body["redirect_uri"]); w.Code != 401 {
		t.Fatal("native grant replayed")
	}
}
