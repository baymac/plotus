package auth

import (
	"encoding/json"
	"errors"
	"net/http"
	"time"

	"github.com/redis/go-redis/v9"
)

type googleChallenge struct{ Nonce, Challenge string }

// Native SDK sign-in binds Google's signed nonce to a short-lived PKCE proof.
// A stolen/replayed Google token cannot create a second Plotus session.
func (s *Server) nativeGoogleChallenge(w http.ResponseWriter, r *http.Request) {
	if s.Google == nil || s.Google.nativeID == "" {
		fail(w, 503, "GOOGLE_NOT_CONFIGURED", "Google sign-in is not configured for this app.")
		return
	}
	var body struct {
		Challenge string `json:"code_challenge"`
		Method    string `json:"code_challenge_method"`
	}
	if decode(r, &body) != nil || body.Method != "S256" || len(body.Challenge) != 43 || !urlSafe.MatchString(body.Challenge) {
		fail(w, 400, "INVALID_REQUEST", "Invalid sign-in proof.")
		return
	}
	attempt, nonce := randomToken(), randomToken()
	if err := s.put(r.Context(), "plotus:google-native:"+digest(attempt), googleChallenge{nonce, body.Challenge}, 5*time.Minute); err != nil {
		authError(w, err)
		return
	}
	writeJSON(w, 200, map[string]string{"attempt": attempt, "nonce": nonce})
}

func (s *Server) nativeGoogleToken(w http.ResponseWriter, r *http.Request) {
	if s.Google == nil || s.Google.nativeID == "" {
		fail(w, 503, "GOOGLE_NOT_CONFIGURED", "Google sign-in is not configured for this app.")
		return
	}
	var body struct {
		Attempt  string `json:"attempt"`
		Token    string `json:"id_token"`
		Verifier string `json:"code_verifier"`
	}
	if decode(r, &body) != nil || len(body.Attempt) != 43 || len(body.Token) > 16384 || body.Token == "" || len(body.Verifier) < 43 || len(body.Verifier) > 128 {
		fail(w, 400, "INVALID_REQUEST", "Invalid Google sign-in request.")
		return
	}
	key := "plotus:google-native:" + digest(body.Attempt)
	raw, err := s.Cache.Get(r.Context(), key).Bytes()
	if err != nil && !errors.Is(err, redis.Nil) {
		authError(w, err)
		return
	}
	var challenge googleChallenge
	if err != nil || json.Unmarshal(raw, &challenge) != nil || !equal(pkce(body.Verifier), challenge.Challenge) {
		fail(w, 401, "INVALID_GRANT", "Sign-in proof is invalid or expired.")
		return
	}
	identity, err := s.Google.verifyIdentity(r.Context(), body.Token, challenge.Nonce, true)
	if err != nil {
		fail(w, 401, "GOOGLE_REJECTED", "Google sign-in could not be verified. Please try again.")
		return
	}
	consumed, err := s.Cache.GetDel(r.Context(), key).Bytes()
	if err != nil && !errors.Is(err, redis.Nil) {
		authError(w, err)
		return
	}
	if err != nil || !equal(string(raw), string(consumed)) {
		fail(w, 401, "INVALID_GRANT", "This sign-in has already been used.")
		return
	}
	user, err := s.Store.GoogleUser(r.Context(), identity.Subject, identity.Email, identity.Name, identity.Picture)
	if err != nil {
		authError(w, err)
		return
	}
	tokens, err := s.issue(w, r, user.ID, "native")
	if err != nil {
		authError(w, err)
		return
	}
	writeJSON(w, 200, tokens)
}
