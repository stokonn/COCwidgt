package dev.stokonn.cocwidgt.security

/**
 * Source compatibility for earlier builds. This alias stores only the official Developer API
 * key; it must never be used for the in-game ownership token.
 */
@Deprecated("Use DeveloperApiKeyStore to make the credential purpose explicit.")
typealias TokenStore = DeveloperApiKeyStore
