# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [1.0.3] - 2026-09-11

- Fix free transfers showing stale (e.g. still 1 after you'd already used it, whether through the
  app or the official site/app) - FPL reports the gameweek's starting free transfer entitlement
  and how many you've made separately, and only the entitlement was being read.
- Add a "Transfers" dropdown to the Transfers tab (defaulting to your actual free transfers, capped
  at 2) so you can force suggestions to be calculated with 0 free transfers, or plan a deliberate
  hit - with a warning showing how many points it'll cost before you submit.

## [1.0.2] - 2026-09-02

- Fix Transfers showing stale suggestions (still calculated, and submittable, against the squad
  from before) after making a transfer - the tab now recalculates against the current squad.
- Add a "Reload" item to the Account menu to refresh My Squad and Transfers on demand.

## [1.0.1] - 2026-09-02

- Fix substitution suggestions on the My Squad pitch flip-flopping between runs (and sometimes
  immediately reversing a suggestion you'd just applied) - tied players were broken by an unseeded
  coin toss; ties are now broken in favour of whoever's already selected, so a swap is only ever
  suggested when the incoming player is a strictly better choice.

## [1.0.0] - 2026-09-02

- Initial version
