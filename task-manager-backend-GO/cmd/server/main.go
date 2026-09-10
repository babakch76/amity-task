// Command server is the TaskFlow API: routing, middleware wiring, startup.
//
// Every route except the two auth endpoints sits behind middleware.Auth, and
// every /groups/{group_id}/... route additionally sits behind
// middleware.RequireMembership, which is the data-siloing guard: a non-member
// gets 404, never 403, so they cannot even confirm a group exists.
//
// Configuration is by environment variable only (see README, "Environment
// variables"): PORT, BIND_ADDR, DB_PATH, JWT_SECRET. The defaults are for
// local development and nothing needs setting to run against the emulator.
package main

import (
	"context"
	"errors"
	"log"
	"net"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"task-manager-backend/internal/auth"
	"task-manager-backend/internal/database"
	"task-manager-backend/internal/handlers"
	"task-manager-backend/internal/middleware"
)

const defaultJWTSecret = "change-me-in-production"

func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func main() {
	log.SetFlags(log.LstdFlags | log.Lmicroseconds)

	secret := env("JWT_SECRET", defaultJWTSecret)
	if secret == defaultJWTSecret {
		log.Println("WARNING: JWT_SECRET is at its default; anyone who has read this source can forge a token. Set it before exposing the server.")
	}
	auth.Init(secret)

	dbPath := env("DB_PATH", "./taskmanager.db")
	db, err := database.New(dbPath)
	if err != nil {
		log.Fatalf("database: %v", err)
	}
	defer db.Close()
	log.Printf("database ready at %s", dbPath)

	addr := net.JoinHostPort(env("BIND_ADDR", ""), env("PORT", "8080"))
	srv := &http.Server{
		Addr:              addr,
		Handler:           logRequests(newRouter(db)),
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       120 * time.Second,
	}

	go func() {
		log.Printf("listening on %s", addr)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("listen: %v", err)
		}
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM)
	<-stop
	log.Println("shutting down")
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(ctx); err != nil {
		log.Printf("shutdown: %v", err)
	}
}

// newRouter wires every endpoint in the README's API reference.
func newRouter(db *database.DB) http.Handler {
	authH := &handlers.AuthHandler{DB: db}
	meH := &handlers.MeHandler{DB: db}
	groupH := &handlers.GroupHandler{DB: db}
	taskH := &handlers.TaskHandler{DB: db}
	choreH := &handlers.ChoreHandler{DB: db}

	// Public: only register and login.
	root := http.NewServeMux()
	root.HandleFunc("POST /auth/register", authH.Register)
	root.HandleFunc("POST /auth/login", authH.Login)

	// Everything else requires a valid Bearer token. Mounting the protected
	// mux at "/" means an unknown path also answers 401 to an anonymous caller,
	// which is what the deploy smoke test relies on.
	protected := http.NewServeMux()
	root.Handle("/", middleware.Auth(protected))

	// Authenticated, not group-scoped.
	protected.HandleFunc("GET /me", meH.GetMe)
	protected.HandleFunc("PATCH /me", meH.UpdateMe)
	protected.HandleFunc("POST /groups", groupH.CreateGroup)
	protected.HandleFunc("GET /groups", groupH.ListMyGroups)
	protected.HandleFunc("GET /invites", groupH.ListMyInvites)
	protected.HandleFunc("POST /invites/redeem", groupH.RedeemInviteCode)
	protected.HandleFunc("PATCH /invites/{invite_id}", groupH.RespondToInvite)

	// Group-scoped: membership guard on every one.
	member := func(pattern string, h http.HandlerFunc) {
		protected.Handle(pattern, middleware.RequireMembership(db, h))
	}

	member("GET /groups/{group_id}", groupH.GetGroup)
	member("GET /groups/{group_id}/members", groupH.ListMembers)
	member("DELETE /groups/{group_id}/members/me", groupH.LeaveGroup)
	member("PUT /groups/{group_id}/members/me/away", groupH.SetAway)
	member("GET /groups/{group_id}/activity", groupH.ListActivity)
	member("POST /groups/{group_id}/invite", groupH.InviteByUsername)
	member("POST /groups/{group_id}/invite-code", groupH.GenerateInviteCode)

	member("POST /groups/{group_id}/tasks", taskH.CreateTask)
	member("GET /groups/{group_id}/tasks", taskH.ListTasks)
	member("PATCH /groups/{group_id}/tasks/{task_id}", taskH.UpdateTask)
	member("DELETE /groups/{group_id}/tasks/{task_id}", taskH.DeleteTask)

	member("POST /groups/{group_id}/chores", choreH.CreateChore)
	member("GET /groups/{group_id}/chores", choreH.ListChores)
	member("PATCH /groups/{group_id}/chores/{chore_id}", choreH.UpdateChore)
	member("DELETE /groups/{group_id}/chores/{chore_id}", choreH.DeleteChore)
	member("GET /groups/{group_id}/chores/{chore_id}/history", choreH.ChoreHistory)
	member("GET /groups/{group_id}/history", choreH.GroupHistory)

	member("GET /groups/{group_id}/occurrences", choreH.ListOccurrences)
	member("PATCH /groups/{group_id}/occurrences/{occurrence_id}", choreH.UpdateOccurrence)
	member("POST /groups/{group_id}/occurrences/{occurrence_id}/pass", choreH.PassOccurrence)
	member("DELETE /groups/{group_id}/occurrences/{occurrence_id}/pass", choreH.UndoPass)
	member("PUT /groups/{group_id}/occurrences/{occurrence_id}/due-date", choreH.SetOccurrenceDueDate)

	return root
}

// logRequests writes one line per request: method, path, status, duration.
// Bodies are never logged here; the client's debug build already logs those.
func logRequests(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		sw := &statusWriter{ResponseWriter: w, status: http.StatusOK}
		next.ServeHTTP(sw, r)
		log.Printf("%s %s -> %d (%s)", r.Method, r.URL.Path, sw.status, time.Since(start).Round(time.Millisecond))
	})
}

type statusWriter struct {
	http.ResponseWriter
	status int
}

func (s *statusWriter) WriteHeader(code int) {
	s.status = code
	s.ResponseWriter.WriteHeader(code)
}
