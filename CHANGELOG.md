# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

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
