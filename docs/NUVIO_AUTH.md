# Nuvio authentication

Relay supports both email/password sign-in and Nuvio's TV QR-login flow directly in
`NuvioConnectScreen`. The QR flow keeps its short-lived session values in memory and
requires explicit approval on the user's phone before exchanging a TV session.

## QR flow

`NuvioApi.startQrLoginSession()` creates a cryptographically random, in-memory device nonce and
calls Nuvio's `start_tv_login_session` RPC. The response is validated before use:

- the device code and expiry must be present;
- the verification URL must be HTTPS, contain no credentials or fragment, use Nuvio's public
  login host, and contain a query payload;
- the poll interval is bounded to 2–30 seconds;
- the server-generated URL is exposed as `NuvioQrLoginSession.verificationUrl`, ready for a QR
  renderer to encode.

`NuvioConnectScreen` polls with `NuvioApi.pollQrLoginSession()` until the status is `APPROVED`,
then calls `NuvioApi.exchangeQrLoginSession(session, approvedPoll)`. The API requires an explicit
`APPROVED` status before token exchange;
unknown statuses never auto-login. The returned `NuvioSession` can be passed to the existing
`NuvioSessionStore.save()` method.

The nonce, code, and verification URL are intentionally not persisted. Cancelling or expiring a
flow should discard the `NuvioQrLoginSession` object. The QR URL is safe to display but should be
treated as a short-lived credential and must not be logged; use `NuvioQrLogin.redactUrl()` for
diagnostics.

## Compatibility

Some older Nuvio deployments expose `start_tv_login_session` without the optional
`p_device_name` parameter. Relay retries without that optional field only when the response
specifically identifies that legacy signature. Existing password login and authenticated RPC
sync remain unchanged.

The screen displays the verification URL and QR code while polling, reports expiry or a failed
approval, and retains the email/password form as a manual fallback. Successful QR or password
authentication is saved through the existing encrypted `NuvioSessionStore`.
