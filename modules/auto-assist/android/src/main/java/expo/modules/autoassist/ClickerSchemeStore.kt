package expo.modules.autoassist

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 选点方案本地持久化（SharedPreferences + JSON）。
 */
object ClickerSchemeStore {
  private const val PREF = "toolbox_clicker_schemes_v1"
  private const val KEY = "schemes"

  data class Scheme(
    val id: String,
    val name: String,
    val intervalMs: Long,
    val points: List<ClickerEngine.Point>,
    val updatedAt: Long,
  )

  fun list(context: Context): List<Scheme> {
    val raw = prefs(context).getString(KEY, null) ?: return emptyList()
    return try {
      val arr = JSONArray(raw)
      buildList {
        for (i in 0 until arr.length()) {
          parseOne(arr.getJSONObject(i))?.let { add(it) }
        }
      }.sortedByDescending { it.updatedAt }
    } catch (_: Exception) {
      emptyList()
    }
  }

  fun save(
    context: Context,
    name: String,
    points: List<ClickerEngine.Point>,
    intervalMs: Long,
    replaceId: String? = null,
  ): Scheme {
    val trimmed = name.trim().ifEmpty { "未命名方案" }
    val id = replaceId ?: "sch_${System.currentTimeMillis()}"
    val scheme = Scheme(
      id = id,
      name = trimmed,
      intervalMs = intervalMs.coerceIn(100L, 10_000L),
      points = points,
      updatedAt = System.currentTimeMillis(),
    )
    val all = list(context).filter { it.id != id }.toMutableList()
    all.add(0, scheme)
    // 最多保留 30 套
    writeAll(context, all.take(30))
    return scheme
  }

  fun delete(context: Context, id: String) {
    writeAll(context, list(context).filter { it.id != id })
  }

  fun get(context: Context, id: String): Scheme? =
    list(context).firstOrNull { it.id == id }

  private fun writeAll(context: Context, schemes: List<Scheme>) {
    val arr = JSONArray()
    schemes.forEach { s ->
      arr.put(
        JSONObject().apply {
          put("id", s.id)
          put("name", s.name)
          put("intervalMs", s.intervalMs)
          put("updatedAt", s.updatedAt)
          put(
            "points",
            JSONArray().also { pa ->
              s.points.forEach { p ->
                pa.put(JSONObject().put("x", p.x.toDouble()).put("y", p.y.toDouble()))
              }
            }
          )
        }
      )
    }
    prefs(context).edit().putString(KEY, arr.toString()).apply()
  }

  private fun parseOne(obj: JSONObject): Scheme? {
    return try {
      val ptsArr = obj.getJSONArray("points")
      val pts = buildList {
        for (i in 0 until ptsArr.length()) {
          val p = ptsArr.getJSONObject(i)
          add(ClickerEngine.Point(p.getDouble("x").toFloat(), p.getDouble("y").toFloat()))
        }
      }
      Scheme(
        id = obj.getString("id"),
        name = obj.getString("name"),
        intervalMs = obj.optLong("intervalMs", 800L),
        points = pts,
        updatedAt = obj.optLong("updatedAt", 0L),
      )
    } catch (_: Exception) {
      null
    }
  }

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
