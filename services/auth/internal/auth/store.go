package auth

import (
	"context"
	_ "embed"
	"errors"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

//go:embed migrations/001_auth.sql
var migration string

//go:embed migrations/002_facebook.sql
var facebookMigration string
var ErrUnauthenticated = errors.New("unauthenticated")

type User struct {
	ID      string `json:"id"`
	Email   string `json:"email"`
	Name    string `json:"name"`
	Picture string `json:"picture"`
}

type Session struct {
	ID, UserID, Kind, RefreshHash, CSRF string
	Expires                             time.Time
}
type Store struct{ Pool *pgxpool.Pool }

func (s *Store) Migrate(ctx context.Context) error {
	_, err := s.Pool.Exec(ctx, migration+"\n"+facebookMigration)
	return err
}
func (s *Store) Health(ctx context.Context) error {
	var present bool
	if err := s.Pool.QueryRow(ctx, "SELECT to_regclass('plotus_auth.sessions') IS NOT NULL").Scan(&present); err != nil {
		return err
	}
	if !present {
		return errors.New("auth schema is missing")
	}
	return nil
}

func (s *Store) GoogleUser(ctx context.Context, subject, email, name, picture string) (User, error) {
	u := User{}
	err := s.Pool.QueryRow(ctx, `INSERT INTO plotus_auth.users (id,google_subject,email,name,picture) VALUES ($1,$2,$3,$4,$5)
        ON CONFLICT (google_subject) DO UPDATE SET email=EXCLUDED.email,name=EXCLUDED.name,picture=EXCLUDED.picture,updated_at=now()
        RETURNING id,email,name,picture`, uuid.NewString(), subject, email, name, picture).Scan(&u.ID, &u.Email, &u.Name, &u.Picture)
	return u, err
}

func (s *Store) FacebookUser(ctx context.Context, subject, email, name, picture string) (User, error) {
	u := User{}
	err := s.Pool.QueryRow(ctx, `INSERT INTO plotus_auth.users (id,facebook_subject,email,name,picture) VALUES ($1,$2,$3,$4,$5)
        ON CONFLICT (facebook_subject) DO UPDATE SET email=EXCLUDED.email,name=EXCLUDED.name,picture=EXCLUDED.picture,updated_at=now()
        RETURNING id,email,name,picture`, uuid.NewString(), subject, email, name, picture).Scan(&u.ID, &u.Email, &u.Name, &u.Picture)
	return u, err
}

func (s *Store) User(ctx context.Context, id string) (User, error) {
	u := User{}
	err := s.Pool.QueryRow(ctx, "SELECT id,email,name,picture FROM plotus_auth.users WHERE id=$1", id).Scan(&u.ID, &u.Email, &u.Name, &u.Picture)
	if errors.Is(err, pgx.ErrNoRows) {
		return u, ErrUnauthenticated
	}
	if err != nil {
		return u, err
	}
	return u, nil
}

func (s *Store) CreateSession(ctx context.Context, session Session) error {
	_, err := s.Pool.Exec(ctx, `INSERT INTO plotus_auth.sessions(id,user_id,kind,refresh_hash,csrf_token,expires_at) VALUES($1,$2,$3,$4,$5,$6)`, session.ID, session.UserID, session.Kind, session.RefreshHash, session.CSRF, session.Expires)
	return err
}

func (s *Store) FindSession(ctx context.Context, raw, kind string) (Session, error) {
	session := Session{}
	err := s.Pool.QueryRow(ctx, `SELECT id,user_id,kind,refresh_hash,csrf_token,expires_at FROM plotus_auth.sessions WHERE refresh_hash=$1 AND kind=$2 AND revoked_at IS NULL AND expires_at>now()`, digest(raw), kind).
		Scan(&session.ID, &session.UserID, &session.Kind, &session.RefreshHash, &session.CSRF, &session.Expires)
	if errors.Is(err, pgx.ErrNoRows) {
		return session, ErrUnauthenticated
	}
	return session, err
}

func (s *Store) Active(ctx context.Context, id, userID string) error {
	var valid bool
	err := s.Pool.QueryRow(ctx, `SELECT EXISTS(SELECT 1 FROM plotus_auth.sessions WHERE id=$1 AND user_id=$2 AND revoked_at IS NULL AND expires_at>now())`, id, userID).Scan(&valid)
	if err != nil {
		return err
	}
	if !valid {
		return ErrUnauthenticated
	}
	return nil
}

func (s *Store) Rotate(ctx context.Context, session Session, newRaw string) error {
	command, err := s.Pool.Exec(ctx, `UPDATE plotus_auth.sessions SET refresh_hash=$1 WHERE id=$2 AND refresh_hash=$3 AND revoked_at IS NULL AND expires_at>now()`, digest(newRaw), session.ID, session.RefreshHash)
	if err != nil {
		return err
	}
	if command.RowsAffected() != 1 {
		return ErrUnauthenticated
	}
	return nil
}
func (s *Store) Revoke(ctx context.Context, id string) error {
	_, err := s.Pool.Exec(ctx, "UPDATE plotus_auth.sessions SET revoked_at=COALESCE(revoked_at,now()) WHERE id=$1", id)
	return err
}
