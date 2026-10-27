package auth

import (
	"context"
	"errors"
	"net/http"
	"time"

	"github.com/coreos/go-oidc/v3/oidc"
	"golang.org/x/oauth2"
)

type Identity struct{ Subject, Email, Name, Picture string }
type GoogleProvider struct {
	config   oauth2.Config
	verifier *oidc.IDTokenVerifier
	client   *http.Client
	nativeID string
}

func NewGoogle(ctx context.Context, c Config) (*GoogleProvider, error) {
	if c.GoogleID == "" {
		return nil, nil
	}
	client := &http.Client{Timeout: 12 * time.Second}
	ctx = oidc.ClientContext(ctx, client)
	provider, err := oidc.NewProvider(ctx, "https://accounts.google.com")
	if err != nil {
		return nil, errors.New("Google OIDC discovery failed")
	}
	return &GoogleProvider{config: oauth2.Config{ClientID: c.GoogleID, ClientSecret: c.GoogleSecret,
		RedirectURL: c.PublicURL + "/api/auth/login/google/callback", Endpoint: provider.Endpoint(), Scopes: []string{oidc.ScopeOpenID, "email", "profile"}},
		verifier: provider.Verifier(&oidc.Config{ClientID: c.GoogleID}), client: client, nativeID: c.GoogleIOSID}, nil
}

func (g *GoogleProvider) URL(state, verifier, nonce string) string {
	return g.config.AuthCodeURL(state, oauth2.AccessTypeOnline, oauth2.S256ChallengeOption(verifier), oauth2.SetAuthURLParam("nonce", nonce))
}
func (g *GoogleProvider) Exchange(ctx context.Context, code, verifier, nonce string) (Identity, error) {
	ctx = context.WithValue(ctx, oauth2.HTTPClient, g.client)
	token, err := g.config.Exchange(ctx, code, oauth2.VerifierOption(verifier))
	if err != nil {
		return Identity{}, errors.New("Google code exchange failed")
	}
	raw, ok := token.Extra("id_token").(string)
	if !ok {
		return Identity{}, errors.New("Google did not return an ID token")
	}
	return g.verifyIdentity(ctx, raw, nonce, false)
}

func (g *GoogleProvider) verifyIdentity(ctx context.Context, raw, nonce string, native bool) (Identity, error) {
	verified, err := g.verifier.Verify(ctx, raw)
	if err != nil {
		return Identity{}, errors.New("Google ID token validation failed")
	}
	var claims struct {
		Nonce    string `json:"nonce"`
		Email    string `json:"email"`
		Verified bool   `json:"email_verified"`
		Name     string `json:"name"`
		Picture  string `json:"picture"`
		Party    string `json:"azp"`
	}
	if err = verified.Claims(&claims); err != nil || verified.Subject == "" || !claims.Verified || claims.Email == "" || !equal(claims.Nonce, nonce) {
		return Identity{}, errors.New("Google identity claims are invalid")
	}
	if native && (g.nativeID == "" || (claims.Party != "" && claims.Party != g.nativeID && claims.Party != g.config.ClientID)) {
		return Identity{}, errors.New("Google authorized party is not a configured Plotus client")
	}
	return Identity{verified.Subject, claims.Email, claims.Name, claims.Picture}, nil
}
