package main

import (
	"context"
	"flag"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/baymac/plotus/services/auth/internal/auth"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/redis/go-redis/v9"
)

func main() {
	migrate := flag.Bool("migrate", false, "apply the auth schema and exit")
	flag.Parse()
	config, err := auth.FromEnv()
	if err != nil {
		log.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	pool, err := pgxpool.New(ctx, config.DatabaseURL)
	if err != nil {
		log.Fatal("cannot configure auth database")
	}
	defer pool.Close()
	if err = pool.Ping(ctx); err != nil {
		log.Fatal("cannot connect to auth database")
	}
	store := &auth.Store{Pool: pool}
	if *migrate {
		if err = store.Migrate(ctx); err != nil {
			log.Fatal("auth migration failed")
		}
		cancel()
		log.Print("auth schema ready")
		return
	}
	options, err := redis.ParseURL(config.RedisURL)
	if err != nil {
		log.Fatal("invalid auth Redis URL")
	}
	cache := redis.NewClient(options)
	defer cache.Close()
	if err = cache.Ping(ctx).Err(); err != nil {
		log.Fatal("cannot connect to auth Redis")
	}
	signer, err := auth.NewSigner(config)
	if err != nil {
		log.Fatal(err)
	}
	google, err := auth.NewGoogle(ctx, config)
	if err != nil {
		log.Fatal("cannot initialize Google identity provider")
	}
	handler, err := auth.New(config, store, cache, signer, google)
	if err != nil {
		log.Fatal("invalid passkey configuration")
	}
	cancel()
	server := &http.Server{Addr: config.Address, Handler: handler.Handler(), ReadHeaderTimeout: 5 * time.Second, ReadTimeout: 15 * time.Second, WriteTimeout: 25 * time.Second, IdleTimeout: 60 * time.Second, MaxHeaderBytes: 16 << 10}
	stopping, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	go func() {
		<-stopping.Done()
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		_ = server.Shutdown(ctx)
	}()
	log.Printf("plotus-auth listening on %s (Google configured: %t)", config.Address, google != nil)
	if err = server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatal("auth server failed")
	}
}
