# AI-Assisted Development Process Log

This file documents how AI tools were used to build SwiftPay, and — more importantly —
where their output was reviewed, corrected, or overridden. This is kept honest and
specific rather than generic, because the reviewing engineer for this role explicitly
cares about *how* AI was supervised, not just that it was used.

Format per entry: what was asked → what the AI produced → what was kept/changed and why.

---

### Example entry (replace with your real ones as you go)

**Component:** Ledger balance update logic
**Tool:** Claude / Codex (name whichever you used)
**Prompt intent:** "Generate the balance transfer logic for a payment"
**AI's first output:** Used a mutable `accounts.balance` column with `UPDATE ... SET
balance = balance - amount`, guarded by a row lock.
**Review finding:** This works for single-instance load but doesn't give an audit trail,
and reasoning about correctness under concurrent partial failures is harder to verify.
**Action taken:** Redirected to double-entry ledger design (immutable `ledger_entries`
rows, balance as a derived/materialized value). See DESIGN.md §2.
**Why this matters for review:** This is exactly the kind of "AI code that compiles but
isn't right for the domain" the JD flags — logged here as evidence of critical review,
not blind acceptance.

---

## Entries

<!-- Add one entry per meaningful AI-assisted change. Keep it concrete:
     what you asked, what came back, what you changed and why.
     Aim for signal over volume — 8-12 real entries beats 40 trivial ones. -->

1.
2.
3.

## Commit Discipline

Commits are made incrementally per logical unit of work (not one large AI-generated
dump), so the git history itself reflects the iterative review process described above.
