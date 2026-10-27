package auth

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/pem"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

func testSigner(t *testing.T) *Signer {
	t.Helper()
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(t.TempDir(), "test-key.pem")
	if err = os.WriteFile(path, pem.EncodeToMemory(&pem.Block{Type: "RSA PRIVATE KEY", Bytes: x509.MarshalPKCS1PrivateKey(key)}), 0600); err != nil {
		t.Fatal(err)
	}
	signer, err := NewSigner(Config{KeyFile: path, Issuer: "plotus-auth", Audience: "plotus-api"})
	if err != nil {
		t.Fatal(err)
	}
	return signer
}

func TestPKCEKnownVector(t *testing.T) {
	// RFC 7636 Appendix B: interoperable proof, not a self-derived expectation.
	if got := pkce("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"); got != "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM" {
		t.Fatal(got)
	}
}
func TestAccessValidation(t *testing.T) {
	signer := testSigner(t)
	raw, err := signer.Access("user", "session")
	if err != nil {
		t.Fatal(err)
	}
	claims, err := signer.Verify(raw)
	if err != nil || claims.Subject != "user" || claims.SessionID != "session" {
		t.Fatal(claims, err)
	}
	for _, change := range []string{"issuer", "audience", "expired", "algorithm", "key"} {
		t.Run(change, func(t *testing.T) {
			c := *claims
			c.RegisteredClaims = claims.RegisteredClaims
			method := jwt.SigningMethodRS256
			token := jwt.NewWithClaims(method, c)
			var key any = signer.key
			switch change {
			case "issuer":
				c.Issuer = "another-authority"
			case "audience":
				c.Audience = jwt.ClaimStrings{"another-api"}
			case "expired":
				c.ExpiresAt = jwt.NewNumericDate(time.Now().Add(-time.Minute))
			case "algorithm":
				token = jwt.NewWithClaims(jwt.SigningMethodHS256, c)
				key = []byte("an untrusted symmetric signing key")
			}
			token.Claims = c
			token.Header["kid"] = signer.kid
			if change == "key" {
				token.Header["kid"] = "unknown"
			}
			bad, err := token.SignedString(key)
			if err != nil {
				t.Fatal(err)
			}
			if _, err = signer.Verify(bad); err == nil {
				t.Fatal("accepted invalid token")
			}
		})
	}
}
func TestNativeRedirectAllowlist(t *testing.T) {
	c := Config{NativeRedirects: []string{"plotus://oauth/callback", "http://127.0.0.1/callback"}}
	for _, raw := range []string{"plotus://oauth/callback", "http://127.0.0.1:49152/callback"} {
		if !c.AllowNativeRedirect(raw) {
			t.Fatal("rejected", raw)
		}
	}
	for _, raw := range []string{"evil://oauth/callback", "plotus://oauth/callback?next=evil", "http://localhost:49152/callback", "http://127.0.0.1.evil:49152/callback", "http://127.0.0.1:49152/elsewhere", "http://127.0.0.1:0/callback", "http://user@127.0.0.1:49152/callback", "http://127.0.0.1:49152/callback#fragment"} {
		if c.AllowNativeRedirect(raw) {
			t.Fatal("accepted", raw)
		}
	}
}
func TestSafeBrowserReturnPath(t *testing.T) {
	if !safeRelative("/projects?sort=recent") {
		t.Fatal("relative path rejected")
	}
	for _, raw := range []string{"https://evil.test/", "//evil.test/", "/\\evil.test/", "/\r\nevil"} {
		if safeRelative(raw) {
			t.Fatal("accepted", raw)
		}
	}
}
