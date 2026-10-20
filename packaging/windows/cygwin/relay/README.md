# Prebuilt Cygwin PTY relay

`prebuilt/provenance.json` identifies the tested ARM64/x64 Windows frontends and
x86_64 Cygwin backend, their compiler commands, and exact source hashes. The source
files remain in `../probe/`, alongside protocol and lifecycle acceptance probes.
MSVC frontends use `/MT`; end-user installation requires no native compiler.

The seed refresh verifies each binary and source hash before retaining them with
the seed. When native code changes, rebuild both frontends and the backend, repeat
protocol/close and production launch acceptance, and replace the provenance together
with the binaries. Native x64 Windows and interactive-app validation remain outstanding.
