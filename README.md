# Fantasy Football

A Spring Boot CLI that fetches live Fantasy Premier League data and:

- builds a "Killer Team" - the highest scoring, affordable, valid 15-man squad buildable from
  scratch (see `calculation.KillerTeamFinder`)
- suggests point-improving transfers for your own squad, within your free transfers and budget
  (see `calculation.TransferSelector`)

Run with `./gradlew bootRun`.

## Fetching your own squad

Transfer suggestions need your actual squad, money available and free transfers. There are two
ways to provide that, controlled by the `FPL_AUTH_ENABLED` environment variable.

### Stub mode (default)

If `FPL_AUTH_ENABLED` isn't set to `true`, `FileSquadProvider` reads a `my-squad.json` file from
the project root. Copy `my-squad.example.json` to `my-squad.json` and edit it:

```json
{
  "freeTransfers": 1,
  "moneyAvailable": 2.5,
  "players": ["Salah", "Haaland", "..."]
}
```

`players` are matched by FPL's short display name (case-insensitive) against live player data.
`my-squad.json` is gitignored, so your picks never get committed. Selling price is approximated as
the current buy price (your real selling price depends on purchase history, which this mode has no
way to know).

### Real fetching

Set `FPL_AUTH_ENABLED=true` and `FPL_API_AUTHORIZATION=<token>` to use `AuthenticatedSquadProvider`
instead, which fetches your actual picks, bank and free transfers from the FPL API.

Premier League's login is a JS-driven identity provider (Ping Identity) with no public API or
OAuth client registration, so this app can't perform the login itself. Instead, capture the bearer
token your own browser already uses:

1. Log in to <https://fantasy.premierleague.com> normally in your browser.
2. Open dev tools -> Network tab, then visit a page that needs your identity (e.g. "Points" or "My
   Team") so it calls the API.
3. Find a request to `fantasy.premierleague.com/api/me/` or `/api/my-team/...`.
4. Copy its `X-Api-Authorization` request header value (verbatim, whatever prefix/format it has).

```bash
FPL_AUTH_ENABLED=true FPL_API_AUTHORIZATION='...' ./gradlew bootRun
```

No session cookie is needed alongside the token - that was tried and confirmed unnecessary.

The token expires periodically; if a run fails with an authentication error, capture a fresh one.
This is inherently a bit fragile since it relies on an undocumented API and an unofficial way in -
it can break if Premier League changes how their frontend authenticates. Automating capture of the
token (via an embedded browser) is planned as part of a future desktop UI.
