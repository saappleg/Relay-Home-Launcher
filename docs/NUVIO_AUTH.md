# Nuvio authentication

Relay keeps its existing email/password sign-in and now has an isolated API-layer implementation
of Nuvio's TV QR-login contract for the TV UI to consume.

## QR flow

`NuvioApi.startQrLoginSession()` creates a cryptographically random, in-memory device nonce and
calls Nuvio's `start_tv_login_session` RPC. The response is validated before use:

- the device code and expiry must be present;
- the verification URL must be HTTPS, contain no credentials or fragment, use Nuvio's public
  login host, and contain a query payload;
- the poll interval is bounded to 2–30 seconds;
- the server-generated URL is exposed as `NuvioQrLoginSession.verificationUrl`, ready for a QR
  renderer to encode.

The UI should poll with `NuvioApi.pollQrLoginSession()` until the status is `APPROVED`, then call
`NuvioApi.exchangeQrLoginSession(session, approvedPoll)`. The API requires an explicit `APPROVED`
status before token exchange;
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

The current scoped implementation is API/model infrastructure. The existing `MainActivity` still
shows its password form until the TV UI is wired to this session state in a separate UI change.
