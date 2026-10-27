package auth

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/subtle"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/pem"
	"errors"
	"math/big"
	"os"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
)

func randomToken() string {
	var b [32]byte
	if _, err := rand.Read(b[:]); err != nil {
		panic("secure random source unavailable")
	}
	return base64.RawURLEncoding.EncodeToString(b[:])
}
func digest(value string) string { h := sha256.Sum256([]byte(value)); return hex.EncodeToString(h[:]) }
func pkce(value string) string {
	h := sha256.Sum256([]byte(value))
	return base64.RawURLEncoding.EncodeToString(h[:])
}
func equal(a, b string) bool { return subtle.ConstantTimeCompare([]byte(a), []byte(b)) == 1 }

type Signer struct {
	key                   *rsa.PrivateKey
	kid, issuer, audience string
}
type Claims struct {
	SessionID string `json:"sid"`
	jwt.RegisteredClaims
}

func NewSigner(c Config) (*Signer, error) {
	data, err := os.ReadFile(c.KeyFile)
	if err != nil {
		return nil, errors.New("cannot read signing key")
	}
	block, _ := pem.Decode(data)
	if block == nil {
		return nil, errors.New("invalid signing key PEM")
	}
	var key *rsa.PrivateKey
	if parsed, e := x509.ParsePKCS8PrivateKey(block.Bytes); e == nil {
		key, _ = parsed.(*rsa.PrivateKey)
	}
	if key == nil {
		key, err = x509.ParsePKCS1PrivateKey(block.Bytes)
		if err != nil {
			return nil, errors.New("signing key must be RSA PKCS8 or PKCS1")
		}
	}
	if key.N.BitLen() < 2048 {
		return nil, errors.New("signing key must have at least 2048 bits")
	}
	if err = key.Validate(); err != nil {
		return nil, errors.New("invalid RSA signing key")
	}
	return &Signer{key, digest(key.N.String())[:16], c.Issuer, c.Audience}, nil
}

func (s *Signer) Access(userID, sessionID string) (string, error) {
	now := time.Now()
	token := jwt.NewWithClaims(jwt.SigningMethodRS256, Claims{sessionID, jwt.RegisteredClaims{
		Issuer: s.issuer, Subject: userID, Audience: jwt.ClaimStrings{s.audience},
		IssuedAt: jwt.NewNumericDate(now), NotBefore: jwt.NewNumericDate(now.Add(-5 * time.Second)),
		ExpiresAt: jwt.NewNumericDate(now.Add(15 * time.Minute)), ID: uuid.NewString(),
	}})
	token.Header["kid"] = s.kid
	return token.SignedString(s.key)
}

func (s *Signer) Verify(raw string) (*Claims, error) {
	claims := &Claims{}
	token, err := jwt.ParseWithClaims(raw, claims, func(token *jwt.Token) (any, error) {
		if token.Header["kid"] != s.kid {
			return nil, errors.New("unknown signing key")
		}
		return &s.key.PublicKey, nil
	}, jwt.WithValidMethods([]string{"RS256"}), jwt.WithIssuer(s.issuer), jwt.WithAudience(s.audience), jwt.WithExpirationRequired(), jwt.WithIssuedAt())
	if err != nil || token == nil || !token.Valid || claims.Subject == "" || claims.SessionID == "" {
		return nil, errors.New("invalid access token")
	}
	return claims, nil
}

func (s *Signer) JWKS() any {
	return map[string]any{"keys": []any{map[string]string{
		"kty": "RSA", "use": "sig", "alg": "RS256", "kid": s.kid,
		"n": base64.RawURLEncoding.EncodeToString(s.key.N.Bytes()),
		"e": base64.RawURLEncoding.EncodeToString(big.NewInt(int64(s.key.E)).Bytes()),
	}}}
}
