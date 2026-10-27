package auth

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"net/http"
	"strings"
	"time"

	"github.com/go-webauthn/webauthn/protocol"
	"github.com/go-webauthn/webauthn/webauthn"
	"github.com/google/uuid"
	"github.com/redis/go-redis/v9"
)

type passkeyUser struct {
	User
	Credentials []webauthn.Credential
}

func (u passkeyUser) WebAuthnID() []byte { return []byte(u.ID) }
func (u passkeyUser) WebAuthnName() string {
	if u.Email != "" {
		return u.Email
	}
	if u.Name != "" {
		return u.Name
	}
	return u.ID
}
func (u passkeyUser) WebAuthnDisplayName() string                { return u.Name }
func (u passkeyUser) WebAuthnCredentials() []webauthn.Credential { return u.Credentials }
func (s *Store) passkeyUser(ctx context.Context, id string) (passkeyUser, error) {
	user, err := s.User(ctx, id)
	u := passkeyUser{User: user}
	if err != nil {
		return u, err
	}
	rows, err := s.Pool.Query(ctx, "SELECT credential FROM plotus_auth.passkeys WHERE user_id=$1", id)
	if err != nil {
		return u, err
	}
	defer rows.Close()
	for rows.Next() {
		var raw []byte
		var c webauthn.Credential
		if err = rows.Scan(&raw); err != nil {
			return u, err
		}
		if err = json.Unmarshal(raw, &c); err != nil {
			return u, err
		}
		u.Credentials = append(u.Credentials, c)
	}
	return u, rows.Err()
}
func (s *Store) addCredential(ctx context.Context, userID string, c webauthn.Credential) error {
	raw, err := json.Marshal(c)
	if err != nil {
		return err
	}
	_, err = s.Pool.Exec(ctx, "INSERT INTO plotus_auth.passkeys(credential_id,user_id,label,credential) VALUES($1,$2,'Passkey',$3)", base64.RawURLEncoding.EncodeToString(c.ID), userID, raw)
	return err
}
func (s *Store) updateCredential(ctx context.Context, userID string, c webauthn.Credential) error {
	raw, err := json.Marshal(c)
	if err != nil {
		return err
	}
	tag, err := s.Pool.Exec(ctx, "UPDATE plotus_auth.passkeys SET credential=$1,last_used_at=now() WHERE credential_id=$2 AND user_id=$3", raw, base64.RawURLEncoding.EncodeToString(c.ID), userID)
	if err != nil {
		return err
	}
	if tag.RowsAffected() != 1 {
		return ErrUnauthenticated
	}
	return nil
}

type passkeyPending struct {
	Session                  webauthn.SessionData
	UserID, CookieHash, Kind string
}

func nativePasskey(r *http.Request) bool { return strings.HasPrefix(r.URL.Path, prefix+"/native/") }
func (s *Server) savePasskey(w http.ResponseWriter, r *http.Request, p passkeyPending) error {
	ticket := randomToken()
	p.CookieHash = digest(ticket)
	if err := s.put(r.Context(), "plotus:passkey:"+digest(ticket), p, 5*time.Minute); err != nil {
		return err
	}
	s.setCookie(w, "passkey", ticket, 5*time.Minute, true)
	return nil
}
func (s *Server) takePasskey(w http.ResponseWriter, r *http.Request, kind string) (passkeyPending, error) {
	ticket := s.cookie(r, "passkey")
	var p passkeyPending
	if ticket == "" {
		return p, ErrUnauthenticated
	}
	raw, err := s.Cache.GetDel(r.Context(), "plotus:passkey:"+digest(ticket)).Bytes()
	s.clearCookie(w, "passkey")
	if errors.Is(err, redis.Nil) {
		return p, ErrUnauthenticated
	}
	if err != nil {
		return p, err
	}
	if json.Unmarshal(raw, &p) != nil || p.Kind != kind || !equal(p.CookieHash, digest(ticket)) {
		return p, ErrUnauthenticated
	}
	return p, nil
}
func (s *Server) passkeyBeginLogin(w http.ResponseWriter, r *http.Request) {
	if !nativePasskey(r) && !s.originOK(r) {
		fail(w, 403, "ORIGIN_DENIED", "Origin is not allowed.")
		return
	}
	options, session, err := s.Passkeys.BeginDiscoverableLogin(webauthn.WithUserVerification(protocol.VerificationRequired))
	if err != nil {
		authError(w, err)
		return
	}
	kind := "web"
	if nativePasskey(r) {
		kind = "native"
	}
	if err = s.savePasskey(w, r, passkeyPending{Session: *session, Kind: kind}); err != nil {
		authError(w, err)
		return
	}
	writeJSON(w, 200, options)
}
func (s *Server) passkeyFinishLogin(w http.ResponseWriter, r *http.Request) {
	if !nativePasskey(r) && !s.originOK(r) {
		fail(w, 403, "ORIGIN_DENIED", "Origin is not allowed.")
		return
	}
	kind := "web"
	if nativePasskey(r) {
		kind = "native"
	}
	pending, err := s.takePasskey(w, r, kind)
	if err != nil {
		authError(w, err)
		return
	}
	var storeErr error
	lookup := func(rawID, userHandle []byte) (webauthn.User, error) {
		id, err := uuid.Parse(string(userHandle))
		if err != nil {
			return nil, ErrUnauthenticated
		}
		user, err := s.Store.passkeyUser(r.Context(), id.String())
		if err != nil {
			storeErr = err
			return nil, err
		}
		for _, c := range user.Credentials {
			if bytes.Equal(c.ID, rawID) {
				return user, nil
			}
		}
		return nil, ErrUnauthenticated
	}
	user, credential, err := s.Passkeys.FinishPasskeyLogin(lookup, pending.Session, r)
	if storeErr != nil {
		authError(w, storeErr)
		return
	}
	if err != nil || credential.Authenticator.CloneWarning {
		fail(w, 401, "PASSKEY_REJECTED", "Passkey verification failed. Try another sign-in method.")
		return
	}
	id := string(user.WebAuthnID())
	if err = s.Store.updateCredential(r.Context(), id, *credential); err != nil {
		authError(w, err)
		return
	}
	tokens, err := s.issue(w, r, id, kind)
	if err != nil {
		authError(w, err)
		return
	}
	writeJSON(w, 200, tokens)
}
func (s *Server) passkeyBeginRegister(w http.ResponseWriter, r *http.Request) {
	claims, err := s.bearer(r)
	if err != nil {
		authError(w, err)
		return
	}
	user, err := s.Store.passkeyUser(r.Context(), claims.Subject)
	if err != nil {
		authError(w, err)
		return
	}
	options, session, err := s.Passkeys.BeginRegistration(user,
		webauthn.WithResidentKeyRequirement(protocol.ResidentKeyRequirementRequired),
		webauthn.WithAuthenticatorSelection(protocol.AuthenticatorSelection{ResidentKey: protocol.ResidentKeyRequirementRequired, UserVerification: protocol.VerificationRequired}),
		webauthn.WithExclusions(webauthn.Credentials(user.Credentials).CredentialDescriptors()),
		webauthn.WithConveyancePreference(protocol.PreferNoAttestation))
	if err != nil {
		authError(w, err)
		return
	}
	if err = s.savePasskey(w, r, passkeyPending{Session: *session, UserID: user.ID, Kind: "register"}); err != nil {
		authError(w, err)
		return
	}
	writeJSON(w, 200, options)
}
func (s *Server) passkeyFinishRegister(w http.ResponseWriter, r *http.Request) {
	claims, err := s.bearer(r)
	if err != nil {
		authError(w, err)
		return
	}
	pending, err := s.takePasskey(w, r, "register")
	if err != nil {
		authError(w, err)
		return
	}
	if pending.UserID != claims.Subject {
		authError(w, ErrUnauthenticated)
		return
	}
	user, err := s.Store.passkeyUser(r.Context(), claims.Subject)
	if err != nil {
		authError(w, err)
		return
	}
	credential, err := s.Passkeys.FinishRegistration(user, pending.Session, r)
	if err != nil {
		fail(w, 400, "PASSKEY_REJECTED", "Passkey registration could not be verified.")
		return
	}
	if err = s.Store.addCredential(r.Context(), user.ID, *credential); err != nil {
		authError(w, err)
		return
	}
	writeJSON(w, 201, map[string]string{"id": base64.RawURLEncoding.EncodeToString(credential.ID), "label": "Passkey"})
}
func (s *Server) passkeyList(w http.ResponseWriter, r *http.Request) {
	claims, err := s.bearer(r)
	if err != nil {
		authError(w, err)
		return
	}
	rows, err := s.Store.Pool.Query(r.Context(), "SELECT credential_id,label,created_at,last_used_at FROM plotus_auth.passkeys WHERE user_id=$1 ORDER BY created_at", claims.Subject)
	if err != nil {
		authError(w, err)
		return
	}
	defer rows.Close()
	result := []map[string]any{}
	for rows.Next() {
		var id, label string
		var created time.Time
		var last *time.Time
		if err = rows.Scan(&id, &label, &created, &last); err != nil {
			authError(w, err)
			return
		}
		result = append(result, map[string]any{"id": id, "label": label, "created_at": created, "last_used_at": last})
	}
	if err = rows.Err(); err != nil {
		authError(w, err)
		return
	}
	writeJSON(w, 200, map[string]any{"credentials": result})
}
func (s *Server) passkeyDelete(w http.ResponseWriter, r *http.Request) {
	claims, err := s.bearer(r)
	if err != nil {
		authError(w, err)
		return
	}
	tag, err := s.Store.Pool.Exec(r.Context(), "DELETE FROM plotus_auth.passkeys WHERE credential_id=$1 AND user_id=$2", r.PathValue("id"), claims.Subject)
	if err != nil {
		authError(w, err)
		return
	}
	if tag.RowsAffected() != 1 {
		fail(w, 404, "NOT_FOUND", "Passkey not found.")
		return
	}
	w.WriteHeader(204)
}
