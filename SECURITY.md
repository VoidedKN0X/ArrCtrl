# Security policy

Thank you for helping keep ArrCtrl and its users safe.

## Reporting a problem

**Please don't report security problems in a public issue.** Report them privately instead:

1. Go to the [Security tab](https://github.com/VoidedKN0X/ArrCtrl/security) of this repo.
2. Click **Report a vulnerability** and describe what you found, how to reproduce it, and which version of ArrCtrl you used.

Only the maintainer can see the report. I'll reply as soon as I can, work on a fix, and credit you in the changelog if you'd like.

## Supported versions

Only the latest release gets security fixes. Please update before reporting, and check the [changelog](CHANGELOG.md) to see whether it's already fixed.

## What counts

- Your API keys, qBittorrent password or session, push address or other data leaking to anyone other than the server it belongs to (your own Radarr, Sonarr, Lidarr, Prowlarr, Bazarr, qBittorrent, autobrr, Cleanuparr or ntfy server), for example through a redirect or an image link
- A changed or untrusted certificate being accepted without you trusting it
- Ways around the app lock, or protected screens showing up in screenshots when they shouldn't
- Another app on the phone being able to make ArrCtrl do something you didn't ask for, for example through an `arrctrl://` link, a shared file or the ntfy push receiver
- Anything that lets someone run code or read files they shouldn't

Problems in Radarr, Sonarr, Lidarr, Prowlarr, Bazarr, qBittorrent, autobrr, Cleanuparr or ntfy themselves should go to that project.

## How releases are protected

Every release is built from this repo by GitHub Actions and signed with the same release key. Its SHA-256 certificate fingerprint is:

```
EF:DF:6A:A1:18:80:D2:85:68:2D:55:9C:48:4E:E7:02:28:24:26:A5:82:A8:10:AF:FC:C7:E8:1D:53:B7:16:CE
```

The [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) only accepts APKs signed with this key.
