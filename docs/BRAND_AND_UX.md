# BalanceTrail brand and UX notes

## Name and positioning

**BalanceTrail** combines two ideas central to transaction reconciliation:

- **Balance** describes the accounting outcome: gateway and ledger records should agree.
- **Trail** describes the engineering outcome: every import, decision, skip, and discrepancy remains traceable.

The name is professional without implying a large distributed platform. The product description remains precise: **a transaction-reconciliation workspace**.

Suggested tagline: **Every transaction, clearly accounted for.**

The name was selected after a targeted web search did not surface a directly competing transaction-reconciliation product with the same name. This is not a trademark clearance; a legal availability search would still be required before commercial launch.

## Interface goals

The refreshed interface is designed as a focused finance-operations workspace rather than a generic CRUD dashboard.

1. A new run is the primary action and appears first.
2. Current processing state is visible without opening a detail page.
3. Summary counts use consistent outcome colors.
4. Run history and exception review stay side by side on wide screens.
5. Dense tables preserve readable labels, aligned numeric columns, and clear selection state.
6. Empty, loading, success, replay, and failure states explain the next useful action.

## Visual system

| Role | Treatment | Meaning |
|---|---|---|
| Primary ink | Deep navy | Trust, structure, primary actions |
| Reconciled | Teal | Matched/complete state |
| Attention | Amber | Amount mismatch or review required |
| Missing | Violet | Ledger entry not found |
| Invalid/failure | Rose | Rejected data or failed run |
| Workspace | Cool off-white | Calm background with high-contrast cards |

The BalanceTrail mark uses three transaction lines ending in outcome points. It is implemented as a small inline SVG in `frontend/src/components/BrandMark.tsx`, so the repository does not depend on a copied or externally licensed logo asset.

## Key screens

### Sign-in

The large-screen layout separates product context from authentication. It explains the three engineering promises demonstrated by the application: chunk-safe imports, idempotent reruns, and auditable results. On smaller screens the supporting panel is removed and the form remains direct.

### Reconciliation control room

The dashboard groups work into four layers:

1. identity, analyst session, and live-polling state;
2. CSV import command panel;
3. selected-run summary metrics;
4. run history and discrepancy investigation.

The information architecture mirrors the operator workflow instead of the backend package structure.

## Accessibility and responsive behavior

- Semantic headings, tables, labels, buttons, status messages, and alerts are retained.
- Keyboard focus uses a visible high-contrast teal outline.
- Statuses combine text, dots, and color rather than relying on color alone.
- Numeric values use tabular alignment where useful.
- Motion is reduced when the operating system requests reduced motion.
- Wide data tables scroll horizontally on small viewports rather than compressing content into unreadable cells.

## Scope boundary

This refresh deliberately avoids a component library, animation framework, charting dependency, or frontend state-management package. React state, Tailwind CSS, semantic HTML, and the existing REST API are sufficient for this portfolio-sized application.
