package auth

import (
	"context"
	"crypto"
	"encoding/json"
	"testing"
	"time"

	"github.com/coreos/go-oidc/v3/oidc"
	"github.com/golang-jwt/jwt/v5"
	"golang.org/x/oauth2"
)

func TestNativeGoogleNonceProofAudienceAndReplay(t *testing.T) {
	s, _ := integrationServer(t)
	googleKey := testSigner(t)
	s.Google = &GoogleProvider{config: oauth2.Config{ClientID: "plotus-test-web"}, nativeID: "plotus-test-ios",
		verifier: oidc.NewVerifier("https://accounts.google.com", &oidc.StaticKeySet{PublicKeys: []crypto.PublicKey{&googleKey.key.PublicKey}}, &oidc.Config{ClientID: "plotus-test-web"})}
	h := s.Handler()
	verifier := randomToken()
	subject := "isolated-google-test-" + randomToken()
	begin := func() (string, string) {
		t.Helper()
		w := request(h, "POST", prefix+"/native/google/challenge", map[string]string{"code_challenge": pkce(verifier), "code_challenge_method": "S256"}, nil)
		if w.Code != 200 {
			t.Fatal(w.Code, w.Body)
		}
		var result map[string]string
		_ = json.Unmarshal(w.Body.Bytes(), &result)
		return result["attempt"], result["nonce"]
	}
	attempt, nonce := begin()
	token := func(audience, party, nonceValue string) string {
		t.Helper()
		value := jwt.NewWithClaims(jwt.SigningMethodRS256, jwt.MapClaims{"iss": "https://accounts.google.com", "aud": audience, "azp": party, "sub": subject, "email": "fixture@example.invalid", "email_verified": true, "name": "Native test fixture", "nonce": nonceValue, "iat": time.Now().Unix(), "exp": time.Now().Add(time.Minute).Unix()})
		raw, err := value.SignedString(googleKey.key)
		if err != nil {
			t.Fatal(err)
		}
		return raw
	}
	exchange := func(attempt, proof, raw string) int {
		return request(h, "POST", prefix+"/native/google/token", map[string]string{"attempt": attempt, "code_verifier": proof, "id_token": raw}, nil).Code
	}
	valid := token("plotus-test-web", "plotus-test-ios", nonce)
	if code := exchange(attempt, randomToken(), valid); code != 401 {
		t.Fatal("wrong proof accepted", code)
	}
	for _, bad := range []string{token("untrusted-client", "plotus-test-ios", nonce), token("plotus-test-web", "untrusted-client", nonce), token("plotus-test-web", "plotus-test-ios", "wrong-nonce")} {
		if code := exchange(attempt, verifier, bad); code != 401 {
			t.Fatal("invalid Google token accepted", code)
		}
	}
	w := request(h, "POST", prefix+"/native/google/token", map[string]string{"attempt": attempt, "code_verifier": verifier, "id_token": valid}, nil)
	if w.Code != 200 {
		t.Fatal("valid identity rejected", w.Code, w.Body)
	}
	var tokens tokenResponse
	_ = json.Unmarshal(w.Body.Bytes(), &tokens)
	t.Cleanup(func() {
		_, _ = s.Store.Pool.Exec(context.Background(), "DELETE FROM plotus_auth.sessions WHERE user_id=$1", tokens.UserID)
		_, _ = s.Store.Pool.Exec(context.Background(), "DELETE FROM plotus_auth.users WHERE id=$1", tokens.UserID)
	})
	if tokens.Access == "" || len(tokens.Refresh) != 43 {
		t.Fatal("missing Plotus session")
	}
	if code := exchange(attempt, verifier, valid); code != 401 {
		t.Fatal("replayed identity accepted", code)
	}
	second, _ := begin()
	if code := exchange(second, verifier, valid); code != 401 {
		t.Fatal("identity accepted for another nonce", code)
	}
	if w = request(h, "GET", prefix+"/user/me", nil, map[string]string{"Authorization": "Bearer " + tokens.Access}); w.Code != 200 {
		t.Fatal("new session is unusable", w.Code)
	}
}
