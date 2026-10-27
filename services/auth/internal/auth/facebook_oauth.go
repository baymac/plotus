package auth

import (
	"encoding/json"
	"errors"
	"net/http"
	"net/url"
	"time"

	"github.com/redis/go-redis/v9"
)

func (s *Server) facebookStart(w http.ResponseWriter, r *http.Request) {
	if !s.originOK(r) {
		fail(w, 403, "ORIGIN_DENIED", "Origin is not allowed.")
		return
	}
	var body struct {
		Redirect  string `json:"redirect"`
		State     string `json:"state"`
		Challenge string `json:"code_challenge"`
		Method    string `json:"code_challenge_method"`
	}
	if decode(r, &body) != nil || !validFacebookProof(body.State, body.Challenge, body.Method) {
		fail(w, 400, "INVALID_REQUEST", "Invalid sign-in proof.")
		return
	}
	if body.Redirect == "" {
		body.Redirect = "/"
	}
	if !safeRelative(body.Redirect) {
		fail(w, 400, "INVALID_REDIRECT", "Use a relative app path.")
		return
	}
	s.facebookTicket(w, r, oauthPending{Redirect: body.Redirect, WebState: body.State, WebChallenge: body.Challenge})
}

func (s *Server) nativeFacebookStart(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Redirect  string `json:"redirect_uri"`
		State     string `json:"state"`
		Challenge string `json:"code_challenge"`
		Method    string `json:"code_challenge_method"`
	}
	if decode(r, &body) != nil || !s.Config.AllowNativeRedirect(body.Redirect) || !validFacebookProof(body.State, body.Challenge, body.Method) {
		fail(w, 400, "INVALID_REQUEST", "Invalid native authorization request.")
		return
	}
	s.facebookTicket(w, r, oauthPending{AppRedirect: body.Redirect, AppState: body.State, AppChallenge: body.Challenge})
}

func validFacebookProof(state, challenge, method string) bool {
	return len(state) >= 20 && len(state) <= 512 && urlSafe.MatchString(state) && len(challenge) == 43 && urlSafe.MatchString(challenge) && method == "S256"
}

// Start on the registered callback origin so its browser-binding cookie works
// even when local development uses a different origin for the web app.
func (s *Server) facebookTicket(w http.ResponseWriter, r *http.Request, pending oauthPending) {
	if s.Facebook == nil {
		fail(w, 503, "FACEBOOK_NOT_CONFIGURED", "Facebook sign-in is not configured.")
		return
	}
	ticket := randomToken()
	if err := s.put(r.Context(), "plotus:facebook-ticket:"+digest(ticket), pending, 5*time.Minute); err != nil {
		authError(w, err)
		return
	}
	target, _ := url.Parse(s.Facebook.callback)
	target.Path = prefix + "/login/facebook/authorize"
	target.RawQuery = url.Values{"request": {ticket}}.Encode()
	writeJSON(w, 200, map[string]string{"authUrl": target.String()})
}

func (s *Server) facebookCookie(w http.ResponseWriter, value string, ttl time.Duration) {
	http.SetCookie(w, &http.Cookie{Name: s.cookieName("facebook_oauth"), Value: value, Path: "/", HttpOnly: true, Secure: true,
		SameSite: http.SameSiteLaxMode, MaxAge: int(ttl.Seconds()), Expires: time.Now().Add(ttl)})
}

func (s *Server) facebookAuthorize(w http.ResponseWriter, r *http.Request) {
	if s.Facebook == nil {
		fail(w, 503, "FACEBOOK_NOT_CONFIGURED", "Facebook sign-in is not configured.")
		return
	}
	ticket := r.URL.Query().Get("request")
	if len(ticket) != 43 || !urlSafe.MatchString(ticket) {
		fail(w, 400, "INVALID_OAUTH_STATE", "Sign-in expired. Start again.")
		return
	}
	raw, err := s.Cache.GetDel(r.Context(), "plotus:facebook-ticket:"+digest(ticket)).Bytes()
	if err != nil && !errors.Is(err, redis.Nil) {
		authError(w, err)
		return
	}
	var pending oauthPending
	if err != nil || json.Unmarshal(raw, &pending) != nil {
		fail(w, 400, "INVALID_OAUTH_STATE", "Sign-in expired. Start again.")
		return
	}
	state, cookie := randomToken(), randomToken()
	pending.CookieHash = digest(cookie)
	if err = s.put(r.Context(), "plotus:facebook-oauth:"+digest(state), pending, 10*time.Minute); err != nil {
		authError(w, err)
		return
	}
	s.facebookCookie(w, cookie, 10*time.Minute)
	http.Redirect(w, r, s.Facebook.URL(state), http.StatusFound)
}

func (s *Server) facebookCallback(w http.ResponseWriter, r *http.Request) {
	raw, err := s.Cache.GetDel(r.Context(), "plotus:facebook-oauth:"+digest(r.URL.Query().Get("state"))).Bytes()
	if err != nil && !errors.Is(err, redis.Nil) {
		authError(w, err)
		return
	}
	var pending oauthPending
	if err != nil || json.Unmarshal(raw, &pending) != nil || !equal(pending.CookieHash, digest(s.cookie(r, "facebook_oauth"))) {
		fail(w, 400, "INVALID_OAUTH_STATE", "Sign-in expired. Start again.")
		return
	}
	s.facebookCookie(w, "", -time.Hour)
	if s.Facebook == nil || r.URL.Query().Get("error") != "" {
		s.facebookFailure(w, r, pending)
		return
	}
	identity, err := s.Facebook.Exchange(r.Context(), r.URL.Query().Get("code"))
	if err != nil {
		s.facebookFailure(w, r, pending)
		return
	}
	user, err := s.Store.FacebookUser(r.Context(), identity.Subject, identity.Email, identity.Name, identity.Picture)
	if err != nil {
		authError(w, err)
		return
	}
	s.completeOAuth(w, r, pending, user)
}

func (s *Server) facebookWebReturn(w http.ResponseWriter, r *http.Request, pending oauthPending, values url.Values) {
	target, _ := url.Parse(s.Config.WebOrigin + pending.Redirect)
	target.Fragment = values.Encode()
	w.Header().Set("Referrer-Policy", "no-referrer")
	http.Redirect(w, r, target.String(), http.StatusFound)
}

func (s *Server) facebookFailure(w http.ResponseWriter, r *http.Request, pending oauthPending) {
	if pending.WebChallenge != "" {
		s.facebookWebReturn(w, r, pending, url.Values{"auth_error": {"facebook_failed"}, "auth_state": {pending.WebState}})
		return
	}
	s.oauthFailure(w, r, pending)
}

func (s *Server) facebookWebExchange(w http.ResponseWriter, r *http.Request) {
	if !s.originOK(r) {
		fail(w, 403, "ORIGIN_DENIED", "Origin is not allowed.")
		return
	}
	s.exchangeGrant(w, r, "web", "plotus:facebook-web-grant:")
}
