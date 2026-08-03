package dev.relay.music.update

import dev.relay.music.extension.ExtensionKind

/** Namespaces prevent a source, theme, or app release with the same ID from colliding. */
enum class UpdatableComponentKind { APPLICATION, EXTENSION, THEME_PACK, WALLPAPER_PRESET }

fun ExtensionKind.toUpdatableComponentKind(): UpdatableComponentKind = when (this) {
    ExtensionKind.SOURCE -> UpdatableComponentKind.EXTENSION
    ExtensionKind.THEME_PACK -> UpdatableComponentKind.THEME_PACK
}

data class ComponentIdentity(
    val kind: UpdatableComponentKind,
    val sourceId: String,
    val id: String,
)

data class InstalledComponent(
    val identity: ComponentIdentity,
    val version: String,
)

data class AvailableComponent<T>(
    val identity: ComponentIdentity,
    val version: String,
    val isCompatible: Boolean,
    val payload: T,
)

enum class ComponentUpdateStatus {
    CURRENT,
    UPDATE_AVAILABLE,
    VERSION_CHANGE,
    DOWNGRADE,
    INCOMPATIBLE,
}

data class ComponentUpdate<T>(
    val installed: InstalledComponent,
    val candidate: AvailableComponent<T>,
    val status: ComponentUpdateStatus,
) {
    val isActionable: Boolean
        get() = status == ComponentUpdateStatus.UPDATE_AVAILABLE || status == ComponentUpdateStatus.VERSION_CHANGE
}

/**
 * Matches installed components to catalog candidates without making installation decisions.
 * Unknown version formats stay visible as [ComponentUpdateStatus.VERSION_CHANGE], never a
 * silently accepted upgrade.
 */
fun <T> findComponentUpdates(
    installed: Iterable<InstalledComponent>,
    candidates: Iterable<AvailableComponent<T>>,
): List<ComponentUpdate<T>> {
    val installedByIdentity = installed.associateBy { it.identity }
    return candidates.mapNotNull { candidate ->
        val current = installedByIdentity[candidate.identity] ?: return@mapNotNull null
        val status = componentUpdateStatus(current.version, candidate.version, candidate.isCompatible)
        ComponentUpdate(current, candidate, status).takeUnless { it.status == ComponentUpdateStatus.CURRENT }
    }
}

fun componentUpdateStatus(
    installedVersion: String,
    candidateVersion: String,
    isCompatible: Boolean,
): ComponentUpdateStatus = when {
    !isCompatible -> ComponentUpdateStatus.INCOMPATIBLE
    installedVersion == candidateVersion -> ComponentUpdateStatus.CURRENT
    else -> when (compareSemanticVersions(candidateVersion, installedVersion)) {
        1 -> ComponentUpdateStatus.UPDATE_AVAILABLE
        -1 -> ComponentUpdateStatus.DOWNGRADE
        else -> ComponentUpdateStatus.VERSION_CHANGE
    }
}

private data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: List<String>?,
)

private val semanticVersionPattern = Regex(
    """^v?(0|[1-9]\d*)(?:\.(0|[1-9]\d*))?(?:\.(0|[1-9]\d*))?(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$""",
)

private fun compareSemanticVersions(left: String, right: String): Int? {
    val leftVersion = left.toSemanticVersion() ?: return null
    val rightVersion = right.toSemanticVersion() ?: return null
    compareValues(leftVersion.major, rightVersion.major).takeIf { it != 0 }?.let { return it }
    compareValues(leftVersion.minor, rightVersion.minor).takeIf { it != 0 }?.let { return it }
    compareValues(leftVersion.patch, rightVersion.patch).takeIf { it != 0 }?.let { return it }
    return comparePrerelease(leftVersion.prerelease, rightVersion.prerelease)
}

private fun String.toSemanticVersion(): SemanticVersion? {
    val match = semanticVersionPattern.matchEntire(this) ?: return null
    return SemanticVersion(
        major = match.groupValues[1].toIntOrNull() ?: return null,
        minor = match.groupValues[2].toIntOrNull() ?: 0,
        patch = match.groupValues[3].toIntOrNull() ?: 0,
        prerelease = match.groupValues[4].ifBlank { null }?.split('.'),
    )
}

private fun comparePrerelease(left: List<String>?, right: List<String>?): Int = when {
    left == null && right == null -> 0
    left == null -> 1
    right == null -> -1
    else -> {
        left.zip(right).forEach { (leftPart, rightPart) ->
            val comparison = comparePrereleasePart(leftPart, rightPart)
            if (comparison != 0) return comparison
        }
        compareValues(left.size, right.size)
    }
}

private fun comparePrereleasePart(left: String, right: String): Int {
    val leftNumber = left.toIntOrNull()
    val rightNumber = right.toIntOrNull()
    return when {
        leftNumber != null && rightNumber != null -> compareValues(leftNumber, rightNumber)
        leftNumber != null -> -1
        rightNumber != null -> 1
        else -> left.compareTo(right)
    }
}
