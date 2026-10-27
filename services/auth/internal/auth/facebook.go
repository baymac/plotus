package auth

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"time"
)

var facebookVersion = regexp.MustCompile(`^v[0-9]{1,3}\.0$`)

type FacebookProvider struct {
	appID, secret, callback, dialog, graph string
	client                                 *http.Client
}

func NewFacebook(c Config) *FacebookProvider {
	if c.FacebookID == "" {
		return nil
	}
	return &FacebookProvider{appID: c.FacebookID, secret: c.FacebookSecret, callback: c.FacebookCallbackURL,
		dialog: "https://www.facebook.com/" + c.FacebookVersion + "/dialog/oauth", graph: "https://graph.facebook.com/" + c.FacebookVersion,
		client: &http.Client{Timeout: 12 * time.Second, CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}}
}

func (f *FacebookProvider) URL(state string) string {
	q := url.Values{"client_id": {f.appID}, "redirect_uri": {f.callback}, "response_type": {"code"},
		"scope": {"public_profile,email"}, "state": {state}}
	return f.dialog + "?" + q.Encode()
}

// Provider tokens stay on the server. Validate the issuing app and user before
// fetching the profile; email is optional and never used to merge accounts.
func (f *FacebookProvider) Exchange(ctx context.Context, code string) (Identity, error) {
	if code == "" || len(code) > 8192 {
		return Identity{}, errors.New("invalid Facebook authorization code")
	}
	var token struct {
		Access string `json:"access_token"`
	}
	q := url.Values{"client_id": {f.appID}, "client_secret": {f.secret}, "redirect_uri": {f.callback}, "code": {code}}
	if f.get(ctx, "/oauth/access_token", q, "", &token) != nil || token.Access == "" {
		return Identity{}, errors.New("Facebook code exchange failed")
	}
	var debug struct {
		Data struct {
			Valid       bool   `json:"is_valid"`
			AppID       string `json:"app_id"`
			UserID      string `json:"user_id"`
			Type        string `json:"type"`
			Expires     int64  `json:"expires_at"`
			DataExpires int64  `json:"data_access_expires_at"`
		} `json:"data"`
	}
	if f.get(ctx, "/debug_token", url.Values{"input_token": {token.Access}}, f.appID+"|"+f.secret, &debug) != nil {
		return Identity{}, errors.New("Facebook token validation failed")
	}
	d := debug.Data
	if !d.Valid || d.AppID != f.appID || d.UserID == "" || d.Type != "USER" || d.Expires <= time.Now().Unix() || (d.DataExpires != 0 && d.DataExpires <= time.Now().Unix()) {
		return Identity{}, errors.New("Facebook token is invalid or expired")
	}
	var profile struct {
		ID, Email, Name string
		Picture         struct {
			Data struct {
				URL string `json:"url"`
			} `json:"data"`
		} `json:"picture"`
	}
	mac := hmac.New(sha256.New, []byte(f.secret))
	_, _ = mac.Write([]byte(token.Access))
	q = url.Values{"fields": {"id,name,email,picture.type(square)"}, "appsecret_proof": {hex.EncodeToString(mac.Sum(nil))}}
	if f.get(ctx, "/me", q, token.Access, &profile) != nil || profile.ID != d.UserID || profile.Name == "" {
		return Identity{}, errors.New("Facebook profile could not be verified")
	}
	return Identity{profile.ID, profile.Email, profile.Name, profile.Picture.Data.URL}, nil
}

func (f *FacebookProvider) get(ctx context.Context, path string, query url.Values, bearer string, dst any) error {
	r, err := http.NewRequestWithContext(ctx, "GET", f.graph+path+"?"+query.Encode(), nil)
	if err != nil {
		return errors.New("Facebook request failed")
	}
	if bearer != "" {
		r.Header.Set("Authorization", "Bearer "+bearer)
	}
	r.Header.Set("Accept", "application/json")
	response, err := f.client.Do(r)
	if err != nil {
		return errors.New("Facebook request failed")
	}
	defer response.Body.Close()
	if response.StatusCode != 200 {
		return errors.New("Facebook request rejected")
	}
	if json.NewDecoder(io.LimitReader(response.Body, 1<<20)).Decode(dst) != nil {
		return errors.New("invalid Facebook response")
	}
	return nil
}
