package auth

import (
	"errors"
	"net/http"
	"strings"
	"time"
)

func authError(w http.ResponseWriter, err error) {
	if errors.Is(err, ErrUnauthenticated) {
		fail(w, 401, "UNAUTHENTICATED", "Sign in to continue.")
		return
	}
	fail(w, 503, "AUTH_UNAVAILABLE", "Authentication is temporarily unavailable.")
}

func (s *Server) bearer(r *http.Request) (*Claims, error) {
	h := r.Header.Get("Authorization")
	if !strings.HasPrefix(h, "Bearer ") {
		return nil, ErrUnauthenticated
	}
	claims, err := s.Signer.Verify(strings.TrimPrefix(h, "Bearer "))
	if err != nil {
		return nil, ErrUnauthenticated
	}
	if err = s.Store.Active(r.Context(), claims.SessionID, claims.Subject); err != nil {
		return nil, err
	}
	return claims, nil
}

func (s *Server) webCredential(r *http.Request, requireCSRF bool) (Session, error) {
	if !s.originOK(r) {
		return Session{}, ErrUnauthenticated
	}
	raw := s.cookie(r, "refresh")
	if raw == "" {
		return Session{}, ErrUnauthenticated
	}
	session, err := s.Store.FindSession(r.Context(), raw, "web")
	if err != nil {
		return session, err
	}
	if requireCSRF && !equal(r.Header.Get("X-CSRF-Token"), session.CSRF) {
		return Session{}, ErrUnauthenticated
	}
	return session, nil
}

func (s *Server) csrf(w http.ResponseWriter, r *http.Request) {
	session, err := s.webCredential(r, false)
	if err != nil {
		authError(w, err)
		return
	}
	writeJSON(w, 200, map[string]string{"csrf_token": session.CSRF})
}
func (s *Server) webAccess(w http.ResponseWriter, r *http.Request) {
	session, err := s.webCredential(r, true)
	if err != nil {
		authError(w, err)
		return
	}
	access, err := s.Signer.Access(session.UserID, session.ID)
	if err != nil {
		authError(w, err)
		return
	}
	writeJSON(w, 200, tokenResponse{Access: access, Type: "Bearer", Expires: 900, UserID: session.UserID})
}
func (s *Server) webRefresh(w http.ResponseWriter, r *http.Request) {
	session, err := s.webCredential(r, true)
	if err != nil {
		authError(w, err)
		return
	}
	tokens, err := s.rotate(r, session)
	if err != nil {
		authError(w, err)
		return
	}
	s.setCookie(w, "refresh", tokens.Refresh, time.Until(session.Expires), true)
	tokens.Refresh = ""
	writeJSON(w, 200, tokens)
}
func (s *Server) webSession(w http.ResponseWriter, r *http.Request) {
	_, err := s.webCredential(r, true)
	if err != nil {
		authError(w, err)
		return
	}
	writeJSON(w, 200, map[string]bool{"revoked": false})
}
func (s *Server) webLogout(w http.ResponseWriter, r *http.Request) {
	session, err := s.webCredential(r, true)
	if err != nil {
		authError(w, err)
		return
	}
	if err = s.Store.Revoke(r.Context(), session.ID); err != nil {
		authError(w, err)
		return
	}
	s.clearCookie(w, "refresh")
	s.clearCookie(w, "csrf")
	w.WriteHeader(204)
}
func (s *Server) rotate(r *http.Request, session Session) (tokenResponse, error) {
	refresh := randomToken()
	access, err := s.Signer.Access(session.UserID, session.ID)
	if err != nil {
		return tokenResponse{}, err
	}
	if err = s.Store.Rotate(r.Context(), session, refresh); err != nil {
		return tokenResponse{}, err
	}
	return tokenResponse{access, refresh, "Bearer", 900, session.CSRF, session.UserID}, nil
}
func (s *Server) nativeCredential(r *http.Request) (Session, error) {
	var body struct {
		Refresh string `json:"refresh_token"`
		CSRF    string `json:"csrf_token"`
	}
	if decode(r, &body) != nil || len(body.Refresh) != 43 {
		return Session{}, ErrUnauthenticated
	}
	session, err := s.Store.FindSession(r.Context(), body.Refresh, "native")
	if err != nil {
		return session, err
	}
	if !equal(body.CSRF, session.CSRF) {
		return Session{}, ErrUnauthenticated
	}
	return session, nil
}
func (s *Server) nativeRefresh(w http.ResponseWriter, r *http.Request) {
	session, err := s.nativeCredential(r)
	if err != nil {
		authError(w, err)
		return
	}
	tokens, err := s.rotate(r, session)
	if err != nil {
		authError(w, err)
		return
	}
	writeJSON(w, 200, tokens)
}
func (s *Server) nativeLogout(w http.ResponseWriter, r *http.Request) {
	session, err := s.nativeCredential(r)
	if err != nil {
		authError(w, err)
		return
	}
	if err = s.Store.Revoke(r.Context(), session.ID); err != nil {
		authError(w, err)
		return
	}
	w.WriteHeader(204)
}
func (s *Server) me(w http.ResponseWriter, r *http.Request) {
	claims, err := s.bearer(r)
	if err != nil {
		authError(w, err)
		return
	}
	user, err := s.Store.User(r.Context(), claims.Subject)
	if err != nil {
		authError(w, err)
		return
	}
	writeJSON(w, 200, user)
}
