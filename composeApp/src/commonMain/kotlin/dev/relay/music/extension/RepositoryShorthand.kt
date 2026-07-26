package dev.relay.music.extension

private val GITHUB_SHORTHAND = Regex("^([A-Za-z0-9._-]+)/([A-Za-z0-9._-]+)$")
private val GITHUB_URL =
    Regex("^https://github\\.com/([A-Za-z0-9._-]+)/([A-Za-z0-9._-]+?)(?:\\.git)?(?:/tree/([A-Za-z0-9._-]+))?/?$")

/**
 * Turns what a user is likely to have on hand into the descriptor URL Relay should read:
 * `owner/repo`, a GitHub page URL, or an already-direct HTTPS link. Returns null when the input
 * is neither — importing still shows the descriptor and its signing key for review either way.
 */
fun repositoryDescriptorUrl(input: String): String? {
    val value = input.trim()
    if (value.isEmpty()) return null
    GITHUB_SHORTHAND.matchEntire(value)?.let { match ->
        val (owner, repository) = match.destructured
        return githubDescriptorUrl(owner, repository, DEFAULT_BRANCH)
    }
    GITHUB_URL.matchEntire(value)?.let { match ->
        val (owner, repository, branch) = match.destructured
        return githubDescriptorUrl(owner, repository, branch.ifEmpty { DEFAULT_BRANCH })
    }
    return value.takeIf { it.startsWith("https://") && !it.contains(' ') }
}

private fun githubDescriptorUrl(owner: String, repository: String, branch: String) =
    "https://raw.githubusercontent.com/$owner/$repository/$branch/repository.json"

private const val DEFAULT_BRANCH = "main"
