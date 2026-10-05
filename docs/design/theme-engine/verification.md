# Theme engine Plan 1 verification and review handoff

Plan 1 implementation and Task 14 verification/review are complete as of 2026-10-04. The single
whole-branch review covered `192443b3..a4e78995`, found four Important and four Minor findings,
and was followed by one combined fix commit `771fd1a1` and one scoped rereview of
`a4e78995..771fd1a1`. Verdict: **all eight original findings addressed; no new Critical/Important
breakage**. One new Minor expression-parity gap is accepted as tracked Plan 2 work, not as corrected.
Native/user acceptance remains pending. Plans 2 and 3 are written but unexecuted.

## Verified implementation and evidence

The fix covers authored UI→semantic→installed colors, secure read-only secret details and defensive
char-array transfer, live dropdown popup/selection styling, pre-mutation nested-update rejection,
move/close-only icon rejection, selected segment medium weight, full named radii and context icon hue.
Regressions cover behavior and ownership, including shared real-host/fake secret/reentrancy contracts,
selected popup pixels, same-variant theme refresh and origin-aware color diagnostics. Bounded
FlatLaf differential checks do not establish full admitted-expression parity; see the known Minor below.

Final stabilized `./gradlew check` passed in **1m 56s**, 99 tasks (15 executed, 84 up-to-date).
Counts from **682 XML reports**, independently summed by the controller: **9,063 tests, 9,057 passed,
zero failures/errors, six existing environment skips**. Exact module totals are retained in
[UI maintenance](../../ui-maintenance.md#combined-final-review-fix-verification). Architecture,
doclint and compiled documentation gates passed. The affected UI/SDK/testkit/host and distribution
gate passed; the affected geometry/choices/secrets/rows run at `-Dflatlaf.uiScale=2` passed all **42** tests.
Documentation-only finalization after that tree did not rerun the full suite; diff and local-link
checks cover that finalization. No production or test code changed during finalization.

The regenerated matrix comprises dark 1x/2x, light 1x, custom 1x and calibration 1x/2x. Each run has
**49 fixture PNGs + 49 metadata JSON files + seven contact sheets + one manifest = 106 files**.
Total: **294 fixture captures, 294 metadata files, 42 sheets, six manifests = 636 files**.
Metadata checks cover 26 node kinds, expected scales, real FlatLaf, motion disabled, nonzero opened
popup-list bounds and absence of transferred sample secret values. Representative changed-surface
inspection covered masked/revealed secrets, selected dropdowns, Credential Vault, sheet corners and
context menus. The controller independently inspected secret/dropdown/Vault captures. Detailed
scope, original references and inspection limits remain in [calibration](calibration.md); this does
not claim fresh visual inspection of all 42 sheets or native acceptance.

## Known Minor and acceptance boundaries

**Resolved in Plan 2 Tasks 2–3 (2026-10-05), independent review pending:** the three probes
below now match pinned FlatLaf 3.7 in `AuthoredUiColorsTest`. Spin ignores relative hue scaling;
percentage channels truncate and HSL channels use pinned float interpolation. The table is retained
as historical failure evidence, not current output. Focused module gate: 128 tests, zero failures.
Native visual acceptance and complete admitted-expression parity are not claimed.

The final review's JBR 25 headless differential probe found these exact ARGB results against
pinned FlatLaf 3.7:

| Expression | Pure generated | FlatLaf 3.7 |
| --- | --- | --- |
| `spin(#00ff00,30,relative)` | `ff00ff00` | `ff00ff7f` |
| `spin(#00ff00,30)` | `ff00ff80` | `ff00ff7f` |
| `rgba(#123456,50%)` | `80123456` | `7f123456` |

The first incorrectly scales hue rotation; the other two differ by one channel unit. **Owner:
Plan 2 Task 2 theme integration, before Task 3 imported-theme visual acceptance.** Add differential
regressions, preserve absolute spin rotation and match pinned materialization or obtain an explicit
rounding-policy ruling. Affected expressions must not receive exact parity acceptance until corrected.
See [maintenance reproduction](../../ui-maintenance.md#known-minor-expression-parity-gap) and
[Plan 2](../../superpowers/plans/2026-10-04-theme-engine-2-consumer-migration.md).

This residual is distinct from the approved compatibility policy: canonical roles intentionally
preserve authored supported UI colors ahead of FlatLaf theme-name/author heuristics. Carbon's authored
TextField `#112233` resolves and installs unchanged instead of the vendor `lighten(Panel,4%)` result.
No vendor-name logic or new provenance machinery belongs in the pure model; full vendor visual parity
is not promised. Some themes may need explicit semantic adjustments during Plan 2 visual acceptance.

Before production cutover, Plan 2 must connect HostedKit motion to live `ui.animations.enabled`,
migrate app/plugin/vendor consumers and remove the exact legacy copies/bridge in the
[temporary-path and migration ledger](temporary-paths.md). Plan 3 owns full application surface
composition and screenshot matching. Native focus, accessibility, window chrome, OS font behavior
and user visual-artifact acceptance remain user-run; gallery review is required before Plan 2 execution.
No GUI, benchmark, AOT training, push or merge was run. This record preserves review/evidence and all
controller decisions independently of the disposable SDD scratch workspace.

## Chronological controller decisions

The following is the complete 32-line controller ruling record at final handoff, reproduced verbatim.

```text
Ruling: Execute Tasks 4–5 as one integrated implementation/review unit — KitContext needs IconRegistry and icons need metrics; avoid nonfunctional stubs — cost if wrong: larger review/commit unit and possible split rework.
Ruling: Execute Tasks 11–12 as one integrated implementation/review unit — adding PluginContext.kit requires a working host immediately — cost if wrong: larger review/commit unit and possible split rework.
Ruling: Run Task 14 whole-branch review as the SDD final review once — both require the same independent gate — cost if wrong: no duplicate review pass.
Ruling: Extract base ThemeToml in Task 1, then add canonical semantics/writer in Task 2 — existing resolver fixtures depend on real TOML parsing; avoid a duplicate test parser — cost if wrong: parser changes span two commits.
Ruling: Omit GlassLevels.resolve(app.config.GlassSettings) from the extracted value; retain validation and translucency behavior — configuration translation belongs to the app adapter in Plan 2 — cost if wrong: adapter signature may need revision.
Ruling: Preserve unknown top-level string metadata through existing metadata map; reject unknown tables/non-string values with origin diagnostics — shared contract requires non-styling metadata preservation without inventing a general schema — cost if wrong: future metadata types require an explicit format extension.
Ruling: Preserve byte-identical upstream IntelliJ resources and scope whitespace=-blank-at-eol attributes to the exact vendored files carrying upstream trailing spaces — provenance outweighs cosmetic normalization, while normal source checks remain strict — cost if wrong: trailing spaces in those specific vendor files are not flagged by Git.
Ruling: Permit documented swing→themeimport use of public data-only FlatLafThemeData — runtime installation and import conversion share existing JSON/color adapter without duplicate serialization; graph stays acyclic — cost if wrong: helper may need a later neutral-package move.
Ruling: Node ids are global per ViewSpec; option/item/row/column/tree data ids are unique within their owning control — independent choices must be able to reuse normal domain ids like none — cost if wrong: validation scope would need a coordinated model/SDK adjustment before release.
Ruling: Pull basic Stack composition from Task8 into Task6 — meaningful sibling identity/reconciliation and paired-secret tests need a real multi-control container — cost if wrong: Stacks is completed across two commits instead of one.
Ruling: Selected/Toggled identify the owning node; generic Action identifies configured action, including picker opening. Payload event consumers resolve optional configured routing actions from the node descriptor, without a duplicate Action event — stable inline-control identity and payload must survive SDK translation — cost if wrong: event-routing consumers and SDK documentation need coordinated adjustment.
Ruling: Add explicit Form.cancelAction (blank disables cancellation; existing constructor delegates blank) and validate at most one PRIMARY — Escape cannot infer cancellation from localized labels — cost if wrong: small model/SDK/consumer signature adjustment.
Ruling: Add app-only EDT KitView.setAvailableHeight(positive logical pixels), default unconstrained, excluding native chrome; apply scaling once and cap form content to85% with scrolling body/reachable footer — host owns available bounds, plugins must not supply arbitrary geometry — cost if wrong: host sizing adapter or cap interpretation must be revised.
Ruling: Permit nonnull blank FormRow.label for aligned helper rows while retaining real field label/accessibility requirements — source helper anatomy reserves an empty label cell — cost if wrong: schema validation and SDK mirror need a small coordinated correction.
Ruling: Add explicit SINGLE/MULTIPLE selection mode to ItemList/Table with old constructor defaultSINGLE; reject multi-selection inSINGLE — checkbox mode cannot be inferred from current selection cardinality — cost if wrong: model/SDK constructor and validation adjustment.
Ruling: Expanded carries owning tree plus item id; add Removed owning list plus item id for ordered remove intent — owner-scoped data ids and domain-free typed actions need unambiguous context — cost if wrong: event producers/SDK projection/consumers need coordinated revision.
Ruling: Add optional Button addAction footer to ItemList, outside domain rows — source ordered list places an add command inside its bounded surface and it must not masquerade as selectable data — cost if wrong: descriptor/layout slot must be revised.
Ruling: Limit virtualized ListItem slots to presentation Text/Icon/Chip/StatusDot/Progress/Strength, Button/MenuButton/Toggle/Choice and recursive INLINE Stack; reject mutable inputs/Secrets/forms/Custom/nested data. Offscreen owners dispose, descriptor-confirmed state and logical focus identity persist, focus(id) indexes and materializes offscreen controls — bounded mounted components must not erase unowned mutable input or create10000 hidden widgets — cost if wrong: additional inline-control kinds require explicit state virtualization support and SDK validation expansion.
Ruling: Correct affected ChoicesTest fixture bounds and exact-color sample coordinates consistently to logical UIScale units in Task9 fixwave — actual2x run exposed test-coordinate error, retain exact role assertions and no production/threshold relaxation — cost if wrong: fixture correction may require another scoped review/retest.
Ruling: Use explicit Header.count as collection-state signal:0 hides count/search, positive keeps search even forfiltered-empty body, absent means unknown and keepsdeclaredsearch. Keep querymounted anddocumentcallerzero requirement — avoid guessing fromEmpty node/text oradding redundantstate — cost if wrong: introduce explicitPanel collectionstate andadjustSDK/callers.
Ruling: Specialized Custom kinds remain generic SDK capabilities, not restricted to editor/remote plugin ids. Host validates matchingkind/id and specialized root shape, records provenance, rejects ordinary forms, recognizes implementation children under approvedroots; Plan2 supplies narrow source-owner allowlists — spec §7 explicitly supports third-party specialized content and diagnoses API discipline rather than a security sandbox — cost if wrong: runtime shape policy/source allowlists need coordinated revision for additional widget families.
Ruling: Existing supported Appearance subscriptions own domain syntax/editor refresh; host applies semantic foreground/background/opacity on registration and updates without vendor adaptation. Keep CustomContent id/kind/component/lifetime signature — reuse established Editor contract and preserve document/caret — cost if wrong: explicit content-refresh callback/API extension and coordinated adapter updates may be needed.
Ruling: Reject duplicate Custom.contentId values across distinct node ids in module and SDK ViewSpecs — per-view registration needs unambiguous content ownership — cost if wrong: shared-content ownership/registration would need an explicit model and coordinated API update.
Ruling: Nominal control metrics denote visible fill/border bounds; retain28/32etc andpaintfullbounds, withkit-ownedexternal3pxfocus/errorhalo thatdoesnotshrinkcontroloralterlayout — originalnormalizedsourcecontrols show28pxfillandouterring; inflatingfootprints woulddisturbsourcegaps — cost if wrong: overlay/clipping ownership orgeometryinterpretation requirescoordinatedrenderer/layoutrevision.
Ruling: Use explicit semantic composition context forcategoryicontiles (sheet/detail/list/sidebar) whileIconSize remainsglyphsize; ordinaryfolderglyphs stayplain, noid/textinference — existinglayout/listrolescanexpresssourceusagewithoutnewdescriptor — cost if wrong: introduceexpliciticontileusagefield andmirrorSDKprojection. VaultgalleryusesPanelhiddenheader+Splitbody+pinnedfooter andsemantic hostbackground.
Ruling: Append Choice.icon explicitnamedartwork forPICKER (blanknone), mirrorSDK/projection; old7argconstructorretainsexistingkeycueforPICKER andblankothers, rejectnonblankunsupportedkinds — sourcecredentiallockcannotbechosenbyid/textorgenericpickerhardcoding — cost if wrong: descriptor/SDKconstructor andcallsiteiconpolicyneedcoordinatedrevision.
Ruling: Append explicit MenuItem.hue inmodule/SDK witholdconstructorNEUTRAL andexhaustiveprojection — sourcecategorymenuhues cannotbeexpressedwithoutcolorliteralsoriconnameinference — cost if wrong: SDK/modelconstructor andmenuhuepolicyrequirescoordinatedrevision.
Ruling: Resolve authored UI colors into the pure theme snapshot using RuntimePlatform and deterministic color evaluation, preserving authored alias/wildcard order, explicit native tokens above UI-derived bases and canonical semantic overrides last; systemColor requires a resolvable explicit fallback or origin-aware diagnostic — kit and installed colors must agree without Swing state in the model — cost if wrong: imported/native precedence or platform evaluation requires resolver and fixture revision.
Ruling: Extend Secret with INPUT/DETAIL and copyAction, retaining the old INPUT constructor; add synchronous defensive-copy replaceSecret(id,char[]) across module/SDK/host/fake, with caller-owned input, wipeable view storage, value-free copy Action and finally-erased withSecret callback copies — stored Vault details need safe read-only reveal/copy without immutable secret descriptors — cost if wrong: coordinated API, secret renderer and consumer integration changes before SDK cutover.
Ruling: Reject reentrant update before mutation at module/host/fake boundaries, contain disposal callback diagnostics, and still permit reentrant close — nested disposal updates must not split committed spec from mounted ownership — cost if wrong: consumers needing nested updates must defer publication or a future serialized update policy must replace this contract.
Ruling: Canonical kit roles use supported authored UI color intent ahead of FlatLaf theme-name/author-specific post-JSON heuristics, with no vendor-name logic or new provenance scheme in the pure model — spec5.1 defines authored→semantic resolution and preserving color intent; Carbon's authored TextField #112233 therefore wins its vendor lighten(Panel,4%) adjustment, while ancillary legacy UI behavior remains — cost if wrong: themes relying on vendor heuristics need explicit semantic adjustment during Plan2 visual acceptance, or a future effective-color import layer. Require concrete regression and honest compatibility documentation.
Ruling: Park the real minor expression compatibility gap after the single final fix wave: spin(color,angle,relative) incorrectly scales hue rotation, and spin/rgba percentage materialization can differ from pinned FlatLaf by one channel unit; assign focused parity correction to Plan2 theme integration before imported-theme acceptance — no later architecture/API task depends on this narrow evaluator edge, and the final review cap permits no second fix wave — cost if wrong: affected imported color expressions render a different hue/alpha until corrected; themes using them must not receive exact parity acceptance.
```
