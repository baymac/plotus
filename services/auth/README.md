# Authentication boundary

Reserved for the next milestone. Neither client currently creates an authenticated session.

Both clients will use one authentication authority and the API will verify sessions independently of client state. Provider secrets belong on the server. Google sign-in and passkeys are design targets, not completed integrations. A local design preview must never become an auth fallback or an API credential.
