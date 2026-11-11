# Signed release validation environment

The `validate-signed-release.yml` workflow uses the existing repository signing secrets only after the `release-signing-validation` environment has passed its protection check. The workflow fails before its secret-bearing build step if the environment is missing a reviewer gate or is not restricted to `main`.

## Configure in GitHub

After this workflow is merged into the default branch, configure it at **Settings → Environments → New environment**:

1. Name it `release-signing-validation`.
2. Under **Deployment branches and tags**, select **Selected branches and tags** and add only the `main` branch.
3. Under **Environment protection rules**, require review from a trusted maintainer who is not the person dispatching the run. Enable **Prevent self-review**. Do not enable administrator bypass.
4. Do not add environment secrets. The workflow uses the four existing repository secrets: `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`.

This environment gate is separate from branch protection. Do not change existing repository or branch protection rules as part of this setup.

## Run validation

Dispatch **Validate signed Android release** from the default branch (`main`) and provide the full commit SHA to validate. The target must be reachable from `feature/v2-village-import-upgrade-widget`. Review the preflight job's commit and change summary before approving the protected signing job.

The workflow checks the APK signature and manifest metadata on the runner, reports the public signer certificate fingerprint and APK checksum, and retains no APK or keystore artifact. It does not create a release or upload an APK.
