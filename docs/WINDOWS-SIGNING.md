# Windows signing for DeX Bridge

DeX Bridge modifies the scrcpy Windows client, so the resulting `scrcpy.exe` is a new binary and cannot retain any trust/reputation associated with an upstream build.

On Windows 11 with Smart App Control in enforcement mode, unknown unsigned binaries can be blocked. Packaging, ZIP checksums, GPG release signatures, renaming the executable, or reusing upstream DLLs do not make the modified executable trusted by Smart App Control.

For a test/release that is expected to run with Smart App Control enabled, DeX Bridge therefore needs Authenticode code signing with a certificate/identity trusted by Windows (for example Microsoft Trusted Signing or a code-signing certificate issued by a CA in the Microsoft Trusted Root Program).

## Policy for this repository

- Do not ask testers to disable Smart App Control, Defender, or SmartScreen globally.
- Do not use a self-signed certificate as a workaround for Smart App Control.
- Keep SHA-256 manifests in artifacts for integrity verification; hashes are complementary to, not a replacement for, trusted code signing.
- Sign the final executable payload after compilation and before packaging.
- Verify signatures in CI before publishing a signed artifact.

## CI signing contract

The signing job is prepared in `.github/workflows/sign-win64.yml` and intentionally requires repository secrets/variables supplied by the repository owner. Signing credentials must never be committed to the repository.

The workflow accepts a PFX through the `DEXBRIDGE_SIGNING_PFX_B64` secret and its password through `DEXBRIDGE_SIGNING_PFX_PASSWORD`. This supports a conventional trusted RSA Authenticode certificate. Microsoft Trusted Signing can be integrated later without changing the Engine code.

Until trusted signing credentials exist, CI may continue producing unsigned engineering artifacts, but those artifacts are not presented as Smart App Control-compatible builds.
