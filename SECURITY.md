# Security Policy

## Report a vulnerability

Please do not report security problems in public issues. Report them privately through GitHub:

1. Open the [Security tab](https://github.com/by-architect/Dejavu/security) of this repository.
2. Click **Report a vulnerability**.
3. Describe the problem, the Dejavu version and the steps to reproduce it.

We will reply as soon as we can, keep you updated while we work on a fix, and, if you like, credit you when the fix
is released.

## What to report here

Report problems in Dejavu's own features here: workspaces, containers, split view, sync with Zen and the rest of the
`org.mozilla.fenix.dejavu` code, and anything about how Dejavu is built and published.

Problems in Firefox's engine or in other code Dejavu shares with Firefox usually affect Firefox too. Please report
those to Mozilla with their [bug bounty form](https://bugzilla.mozilla.org/form.client.bounty), which also makes them
eligible for Mozilla's [client bug bounty program](https://www.mozilla.org/en-US/security/client-bug-bounty/). If you
are not sure where a problem belongs, report it here and we will help.

## Supported versions

Security fixes are made for the latest Dejavu release, on GitHub and in the ByArchitect F-Droid repository.

Dejavu's engine does not follow Firefox's releases yet. It is a Firefox 159 Nightly build from September 25, 2026, so
fixes that Mozilla makes in Firefox's engine after that do not reach Dejavu until it moves to a newer Firefox. Moving
to Firefox's releases, and then following each one, is on the
[roadmap](https://github.com/users/by-architect/projects/1).

## Check a download

APKs from GitHub and the ByArchitect F-Droid repository are signed with Dejavu's key. Its certificate has this SHA-256
fingerprint, which apps like [AppVerifier](https://github.com/soupslurpr/AppVerifier) compare with the installed app:

```
AD:2C:4C:E3:BD:3A:A1:30:A4:3C:6E:6F:81:4A:14:7C:91:80:EE:3F:C1:80:A1:89:54:87:06:64:F1:47:F1:4A
```

Each release also lists the SHA-256 checksum of every file it offers.
