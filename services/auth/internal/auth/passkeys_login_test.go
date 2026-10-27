package auth

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"net/http/httptest"
	"testing"

	"github.com/fxamacker/cbor/v2"
	"github.com/go-webauthn/webauthn/protocol"
	"github.com/go-webauthn/webauthn/webauthn"
	"github.com/google/uuid"
)

// Use real signatures and a backed-up platform credential, like an iPhone
// passkey subsequently used by a browser on another device.
func TestSyncedPasskeyLoginAndRemoval(t *testing.T) {
	s, user := integrationServer(t)
	private, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	key, err := cbor.Marshal(map[int]any{1: 2, 3: -7, -1: 1, -2: private.X.FillBytes(make([]byte, 32)), -3: private.Y.FillBytes(make([]byte, 32))})
	if err != nil {
		t.Fatal(err)
	}
	credential := webauthn.Credential{
		ID: []byte(randomToken()), PublicKey: key, AttestationType: "none",
		Flags:         webauthn.NewCredentialFlags(protocol.AuthenticatorFlags(0x1d)),
		Authenticator: webauthn.Authenticator{AAGUID: make([]byte, 16), Attachment: protocol.Platform},
	}
	if err = s.Store.addCredential(context.Background(), user.ID, credential); err != nil {
		t.Fatal(err)
	}
	encode := base64.RawURLEncoding.EncodeToString
	login := func(handle string, wrongKey, expired bool) *httptest.ResponseRecorder {
		t.Helper()
		headers := map[string]string{"Origin": s.Config.WebOrigin}
		begun := request(s.Handler(), "POST", prefix+"/passkey/login/begin", map[string]any{}, headers)
		if begun.Code != 200 {
			t.Fatal("begin", begun.Code)
		}
		var options struct {
			PublicKey struct {
				Challenge string `json:"challenge"`
			} `json:"publicKey"`
		}
		if err = json.Unmarshal(begun.Body.Bytes(), &options); err != nil {
			t.Fatal(err)
		}
		cookie := begun.Result().Cookies()[0]
		headers["Cookie"] = cookie.Name + "=" + cookie.Value
		if expired {
			if err = s.Cache.Del(context.Background(), "plotus:passkey:"+digest(cookie.Value)).Err(); err != nil {
				t.Fatal(err)
			}
		}
		client, err := json.Marshal(map[string]any{"type": "webauthn.get", "challenge": options.PublicKey.Challenge, "origin": s.Config.WebOrigin})
		if err != nil {
			t.Fatal(err)
		}
		rpHash := sha256.Sum256([]byte(s.Config.RPID))
		authData := append(append([]byte{}, rpHash[:]...), 0x1d)
		count := make([]byte, 4)
		binary.BigEndian.PutUint32(count, 0)
		authData = append(authData, count...)
		clientHash := sha256.Sum256(client)
		signed := append(append([]byte{}, authData...), clientHash[:]...)
		digest := sha256.Sum256(signed)
		signing := private
		if wrongKey {
			signing, err = ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
			if err != nil {
				t.Fatal(err)
			}
		}
		signature, err := ecdsa.SignASN1(rand.Reader, signing, digest[:])
		if err != nil {
			t.Fatal(err)
		}
		body := map[string]any{"id": encode(credential.ID), "rawId": encode(credential.ID), "type": "public-key", "authenticatorAttachment": "platform", "response": map[string]string{
			"clientDataJSON": encode(client), "authenticatorData": encode(authData), "signature": encode(signature), "userHandle": encode([]byte(handle)),
		}, "clientExtensionResults": map[string]any{}}
		return request(s.Handler(), "POST", prefix+"/passkey/login/finish", body, headers)
	}
	code := func(w *httptest.ResponseRecorder) string {
		t.Helper()
		var result struct {
			Error struct {
				Code string `json:"code"`
			} `json:"error"`
		}
		if err := json.Unmarshal(w.Body.Bytes(), &result); err != nil {
			t.Fatal("expected JSON error", w.Code)
		}
		return result.Error.Code
	}
	succeeded := login(user.ID, false, false)
	if succeeded.Code != 200 {
		t.Fatal("synced credential rejected", succeeded.Code, succeeded.Body)
	}
	var tokens tokenResponse
	if err = json.Unmarshal(succeeded.Body.Bytes(), &tokens); err != nil || tokens.UserID != user.ID || tokens.Access == "" || tokens.Refresh != "" {
		t.Fatal("invalid browser session")
	}
	if w := login(user.ID, true, false); w.Code != 401 || code(w) != "PASSKEY_REJECTED" {
		t.Fatal("signature error marked as removed", w.Code, w.Body)
	}
	if w := login(uuid.NewString(), false, false); w.Code != 401 || code(w) != "PASSKEY_REJECTED" {
		t.Fatal("wrong user handle marked as removed", w.Code, w.Body)
	}
	if w := login(user.ID, false, true); w.Code != 401 || code(w) == "PASSKEY_UNKNOWN" {
		t.Fatal("expired prompt marked as removed", w.Code, w.Body)
	}
	listing := request(s.Handler(), "GET", prefix+"/passkey/credentials", nil, map[string]string{"Authorization": "Bearer " + tokens.Access})
	var listed struct {
		RPID string `json:"rp_id"`
	}
	if listing.Code != 200 || json.Unmarshal(listing.Body.Bytes(), &listed) != nil || listed.RPID != s.Config.RPID {
		t.Fatal("missing credential-manager relying party")
	}
	removed := request(s.Handler(), "DELETE", prefix+"/passkey/credentials/"+encode(credential.ID), nil, map[string]string{"Authorization": "Bearer " + tokens.Access})
	if removed.Code != 204 {
		t.Fatal("remove", removed.Code, removed.Body)
	}
	if w := login(user.ID, false, false); w.Code != 401 || code(w) != "PASSKEY_UNKNOWN" {
		t.Fatal("removed credential not identified", w.Code, w.Body)
	}
	var count int
	if err = s.Store.Pool.QueryRow(context.Background(), "SELECT count(*) FROM plotus_auth.sessions WHERE user_id=$1", user.ID).Scan(&count); err != nil || count != 1 {
		t.Fatal("invalid login created a session", count, err)
	}
}
