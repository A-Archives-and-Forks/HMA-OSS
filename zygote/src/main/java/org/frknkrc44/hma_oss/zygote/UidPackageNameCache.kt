package org.frknkrc44.hma_oss.zygote

class UidPackageNameCache private constructor() {
    companion object {
        val instance by lazy { UidPackageNameCache() }
    }

    private val uidAppsCache = mutableListOf<Pair<Int, MutableSet<String>>>()

    fun findCacheEntryByUid(uid: Int) = uidAppsCache.firstOrNull { it.first == uid }

    fun findCacheEntryByPackageName(packageName: String) = uidAppsCache.firstOrNull { packageName in it.second }

    fun isPackageNameExists(packageName: String) = uidAppsCache.any { packageName in it.second }

    fun addCachedAppEntry(uid: Int, packageName: String) {
        val entry = findCacheEntryByUid(uid)
        if (entry == null) {
            uidAppsCache.add(
                Pair(uid, mutableSetOf(packageName))
            )
        } else {
            entry.second.add(packageName)
        }
    }

    fun removeCachedUidEntry(uid: Int) = uidAppsCache.removeIf { it.first == uid }

    fun removeCachedAppEntry(packageName: String): Boolean {
        val entry = findCacheEntryByPackageName(packageName) ?: return false

        return if (entry.second.size < 2) {
            removeCachedUidEntry(entry.first)
        } else {
            entry.second.removeIf { it == packageName }
        }
    }
}