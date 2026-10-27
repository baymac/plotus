package auth

import (
	"errors"
	"net/url"
	"os"
	"strconv"
	"strings"
)

type Config struct {
	Address, DatabaseURL, RedisURL, PublicURL, WebOrigin, Issuer, Audience, KeyFile string
	GoogleID, GoogleSecret, RPID                                                    string
	NativeRedirects, RPOrigins                                                      []string
	Production                                                                      bool
}

func FromEnv() (Config, error) {
	c := Config{
		Address: env("AUTH_ADDRESS", "127.0.0.1:4001"), DatabaseURL: os.Getenv("AUTH_DATABASE_URL"),
		RedisURL:  env("AUTH_REDIS_URL", "redis://127.0.0.1:6381/0"),
		PublicURL: strings.TrimRight(env("AUTH_PUBLIC_URL", "http://localhost:4001"), "/"),
		WebOrigin: env("AUTH_WEB_ORIGIN", "http://localhost:8081"),
		Issuer:    env("AUTH_ISSUER", "plotus-auth"), Audience: env("AUTH_AUDIENCE", "plotus-api"),
		KeyFile: os.Getenv("AUTH_SIGNING_KEY_FILE"), GoogleID: os.Getenv("GOOGLE_CLIENT_ID"),
		GoogleSecret:    os.Getenv("GOOGLE_CLIENT_SECRET"),
		RPID:            env("WEBAUTHN_RP_ID", "localhost"),
		RPOrigins:       split(env("WEBAUTHN_ORIGINS", "http://localhost:8081")),
		NativeRedirects: split(env("AUTH_NATIVE_REDIRECTS", "plotus://oauth/callback,http://127.0.0.1/callback")),
		Production:      os.Getenv("AUTH_ENV") == "production",
	}
	return c, c.Validate()
}

func (c Config) Validate() error {
	if c.DatabaseURL == "" || c.KeyFile == "" {
		return errors.New("AUTH_DATABASE_URL and AUTH_SIGNING_KEY_FILE are required")
	}
	for _, origin := range []string{c.PublicURL, c.WebOrigin} {
		u, err := url.Parse(origin)
		if err != nil || u.Host == "" || u.User != nil || u.RawQuery != "" || u.Fragment != "" || (u.Scheme != "https" && u.Scheme != "http") {
			return errors.New("invalid auth URL/origin")
		}
		if u.Path != "" && u.Path != "/" {
			return errors.New("auth URL/origin must not include a path")
		}
		if c.Production && u.Scheme != "https" {
			return errors.New("production authentication requires HTTPS")
		}
		if !c.Production && u.Scheme == "http" && u.Hostname() != "localhost" && u.Hostname() != "127.0.0.1" && u.Hostname() != "::1" {
			return errors.New("development HTTP is restricted to loopback")
		}
	}
	if (c.GoogleID == "") != (c.GoogleSecret == "") {
		return errors.New("set both Google OAuth credentials, or neither")
	}
	for _, redirect := range c.NativeRedirects {
		if !c.AllowNativeRedirect(redirect) {
			return errors.New("invalid native redirect allowlist")
		}
	}
	return nil
}

func (c Config) AllowNativeRedirect(raw string) bool {
	u, err := url.Parse(raw)
	if err != nil || u.User != nil || u.RawQuery != "" || u.Fragment != "" || u.Host == "" {
		return false
	}
	if u.Scheme == "http" && u.Hostname() != "127.0.0.1" && u.Hostname() != "::1" {
		return false
	}
	if u.Scheme == "" || u.Scheme == "javascript" || u.Scheme == "data" || u.Scheme == "file" {
		return false
	}
	if u.Port() != "" {
		port, err := strconv.Atoi(u.Port())
		if err != nil || port < 1 || port > 65535 {
			return false
		}
	}
	for _, allowed := range c.NativeRedirects {
		if raw == allowed {
			return true
		}
		a, err := url.Parse(allowed)
		if err != nil {
			continue
		}
		// Desktop apps bind an ephemeral loopback port, as specified by RFC 8252.
		if a.Scheme == "http" && (a.Hostname() == "127.0.0.1" || a.Hostname() == "::1") && a.Port() == "" &&
			u.Scheme == a.Scheme && u.Hostname() == a.Hostname() && u.Path == a.Path && u.Port() != "" {
			return true
		}
	}
	return false
}

func env(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}
func split(value string) []string {
	var result []string
	for _, item := range strings.Split(value, ",") {
		if item = strings.TrimSpace(item); item != "" {
			result = append(result, item)
		}
	}
	return result
}
