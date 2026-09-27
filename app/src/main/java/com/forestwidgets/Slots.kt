package com.forestwidgets

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore

/** 홈 화면 칸마다 어떤 앱이 들어가는지 저장 + 처음 한 번 자동 배치 */
object Slots {

    val GROUPS = linkedMapOf(
        "dock" to 5, "bar0" to 4, "bar1" to 4,
        "box0" to 6, "box1" to 6, "box2" to 6, "box3" to 6,
    )

    // 원형 바에 먼저 넣을 인기 앱 (설치된 것만)
    private val PRIORITY = listOf(
        "com.kakao.talk", "com.google.android.youtube", "com.instagram.android", "com.nhn.android.search",
        "com.spotify.music", "com.iloen.melon", "com.google.android.apps.youtube.music", "com.zhiliaoapp.musically",
        "com.twitter.android", "com.discord", "com.netflix.mediaclient", "com.google.android.apps.maps",
        "com.nhn.android.nmap", "com.coupang.mobile", "com.samsung.android.calendar", "com.sec.android.app.clockpackage",
        "com.samsung.android.app.notes", "com.google.android.gm", "com.android.chrome", "com.android.settings",
    )

    private fun p(ctx: Context) = ctx.getSharedPreferences("forest", Context.MODE_PRIVATE)

    fun isInitialized(ctx: Context) = p(ctx).getBoolean("slots_init", false)

    fun get(ctx: Context, group: String): List<String?> {
        val n = GROUPS[group] ?: 0
        val raw = (p(ctx).getString("slots_$group", "") ?: "").split("|")
        return List(n) { i -> raw.getOrNull(i)?.takeIf { it.isNotBlank() } }
    }

    fun set(ctx: Context, group: String, index: Int, pkg: String?) {
        val list = get(ctx, group).toMutableList()
        if (index !in list.indices) return
        list[index] = pkg
        save(ctx, group, list)
    }

    private fun save(ctx: Context, group: String, list: List<String?>) {
        p(ctx).edit().putString("slots_$group", list.joinToString("|") { it ?: "" }).apply()
    }

    fun reset(ctx: Context) {
        val e = p(ctx).edit()
        GROUPS.keys.forEach { e.remove("slots_$it") }
        e.putBoolean("slots_init", false).apply()
    }

    /** installed: 라벨 순으로 정렬된 설치 앱 목록 */
    fun assignDefaults(ctx: Context, installed: List<String>) {
        val pm = ctx.packageManager
        val set = installed.toSet()
        val used = LinkedHashSet<String>()

        fun take(pkg: String?): String? =
            pkg?.takeIf { it in set && it !in used }?.also { used.add(it) }

        fun resolve(i: Intent): String? = try {
            take(pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName)
        } catch (e: Exception) { null }

        fun selector(cat: String) = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, cat)

        val dock = mutableListOf<String?>(
            resolve(Intent(Intent.ACTION_DIAL)),
            resolve(selector(Intent.CATEGORY_APP_MESSAGING)),
            resolve(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
            resolve(selector(Intent.CATEGORY_APP_GALLERY)),
            resolve(selector(Intent.CATEGORY_APP_BROWSER)),
        )
        val rest = ArrayDeque(installed.filter { it != ctx.packageName })
        fun next(prefer: List<String> = emptyList()): String? {
            for (pkg in prefer) take(pkg)?.let { return it }
            while (rest.isNotEmpty()) { take(rest.removeFirst())?.let { return it } }
            return null
        }
        for (i in dock.indices) if (dock[i] == null) dock[i] = next(PRIORITY)

        val bars = List(8) { next(PRIORITY) }
        save(ctx, "dock", dock)
        save(ctx, "bar0", bars.subList(0, 4))
        save(ctx, "bar1", bars.subList(4, 8))
        for (b in 0 until 4) save(ctx, "box$b", List(6) { next() })
        p(ctx).edit().putBoolean("slots_init", true).apply()
    }
}
