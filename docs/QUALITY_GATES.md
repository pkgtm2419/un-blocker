# Quality gate and PR checklist

Use this as a checklist in every non-trivial PR:

- [ ] Scope, product intent and existing source behavior verified.
- [ ] No unapproved semantic change in `DecideBlockingUseCase` precedence.
- [ ] No DNS packet parsing/forwarding regression or new network loop.
- [ ] No plaintext private learning, unwanted logging/export, analytics or backup leak.
- [ ] No unsafe rule source, unchecked redirect/private-network target or license omission.
- [ ] Android permission, VPN lifecycle, boot/FGS and device network transitions respected.
- [ ] Unit/instrumentation/fuzz/migration cases added or explicitly marked not applicable.
- [ ] `./gradlew testDebugUnitTest lintDebug assembleDebug` run or honestly marked NOT RUN.
- [ ] Python compiler, script hygiene and release checks run as relevant.
- [ ] Root/user docs, feature and module docs, changelog and behavior contracts reviewed.
- [ ] `python3 scripts/docs_sync.py --write` and `--check` run in repo checkout.
- [ ] `python3 scripts/docs_contract.py` and docs-tooling tests pass.
- [ ] Secret scan, dependency/license review, CI branch protections and signing unaffected.
- [ ] PR body records actual evidence, risk level, rollback and release decision.

**Do not conflate check existence, CI success and actual on-device production readiness.**
